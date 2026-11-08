package com.ioteste.engine.integration;

import com.ioteste.core.HeatingController;
import com.ioteste.core.PeakSchedule;
import com.ioteste.core.HeatingController.RoomConfig;
import com.ioteste.core.HeatingController.RoomObservation;
import com.ioteste.core.HeatingController.SiteConfig;
import com.ioteste.core.HeatingController.ControlState;
import com.ioteste.core.HeatingController.SwitchState;
import com.ioteste.core.HeatingController.SwitchAction;

import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;
import com.ioteste.engine.time.TickPlanner;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HeatingEngine {

    private static final Logger log =
            LoggerFactory.getLogger(HeatingEngine.class);

    // Cada cuánto se evalúa si no hay un cambio de franja más cercano
    // (tiempo real). TickLoop pide la espera con nextDelay().
    static final Duration REGULAR_PERIOD = Duration.ofSeconds(1);

    // Evita un bucle sin pausa si el reloj virtual todavía está
    // a un instante del límite al despertar.
    static final Duration MINIMUM_DELAY = Duration.ofMillis(20);

    private static final long PEAK_SHUTDOWN_LEAD_MS = 2000L;

    // Tolerancia para comparar sumas de potencias decimales.
    private static final long POWER_TOLERANCE_MILLI_KW = 0L;

    private final SiteService siteService;
    private final ControlService controlService;
    private final TemperatureSubscriber subscriber;
    private final SwitchClient switches;

    private final HeatingController core = new HeatingController();

    // Instante virtual usado en la última evaluación (null si el control
    // estaba detenido). Permite repetir el ciclo si un límite de franja
    // se cruzó mientras el ciclo estaba ocupado.
    private volatile Instant lastEvaluated;

    public HeatingEngine(
            SiteService siteService,
            ControlService controlService,
            TemperatureSubscriber subscriber,
            SwitchClient switches) {

        this.siteService = siteService;
        this.controlService = controlService;
        this.subscriber = subscriber;
        this.switches = switches;
    }

    /**
     * Un ciclo de control. Lo invoca TickLoop; no usa @Scheduled porque
     * la espera hasta el próximo ciclo depende del reloj virtual.
     */
    public synchronized void tick() {
        try {
            ControlStatus control = controlService.status();

            if (control.estado() != ControlStatus.State.EN_EJECUCION) {
                lastEvaluated = null;
                return;
            }

            SiteService.Snapshot snapshot = siteService.snapshot();

            // Procesar los apagados pendientes antes de nuevas decisiones.
            // Cada habitación se intenta por separado: que un switch no
            // responda no impide apagar los demás.
            if (!shutDownPendingRooms(snapshot, control)) {
                return;
            }

            if (!siteService.confirmShutdowns(snapshot.revision())) {
                return;
            }

            evaluate(snapshot, control);

        } catch (Exception exception) {
            log.warn(
                    "Ciclo de control interrumpido ({}): {}. Reintentando.",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }

    /**
     * Tiempo REAL a esperar hasta el próximo ciclo: el período normal,
     * o menos si el reloj virtual llega antes a un cambio de franja.
     * Nunca lanza excepciones: ante cualquier duda usa el período normal.
     */
    public Duration nextDelay() {
        try {
            SiteInventory inventory = siteService.get();

            ControlStatus control;
            ZoneId zone;

            synchronized (controlService) {
                control = controlService.status();
                zone = controlService.zone();
            }

            boolean running =
                    control.estado() == ControlStatus.State.EN_EJECUCION;

            long factor = control.factorTiempo() == null
                    ? 1000L
                    : control.factorTiempo();

            return TickPlanner.nextDelay(
                    running,
                    control.fechaHora(),
                    lastEvaluated,
                    factor,
                    zone,
                    peakSchedule(inventory),
                    REGULAR_PERIOD,
                    MINIMUM_DELAY
            );

        } catch (Exception exception) {
            return REGULAR_PERIOD;
        }
    }

    private boolean shutDownPendingRooms(
            SiteService.Snapshot snapshot,
            ControlStatus control) {

        boolean allConfirmed = true;
        List<SiteInventory.Room> completed = new ArrayList<>();

        for (SiteInventory.Room room : snapshot.pendingShutdowns()) {
            if (!belongsToCurrentSite(snapshot, room)) {
                completed.add(room);

                log.info(
                        "Se descarta el pendiente de {}: ya no pertenece al sitio vigente.",
                        room.idSwitch()
                );
                continue;
            }

            try {
                SwitchClient.State confirmed = commandIfCurrent(
                        snapshot,
                        control,
                        room,
                        SwitchClient.State.OFF
                );

                if (confirmed == null) {
                    if (!completed.isEmpty()) {
                        siteService.confirmShutdowns(
                                snapshot.revision(),
                                completed
                        );
                    }
                    return false;
                }

                if (confirmed != SwitchClient.State.OFF) {
                    throw new IllegalStateException(
                            "El switch no confirmó OFF"
                    );
                }

                completed.add(room);

                log.info("Switch anterior {} -> OFF", room.idSwitch());

            } catch (Exception exception) {
                allConfirmed = false;

                log.warn(
                        "No se pudo apagar el switch anterior {} ({}): {}",
                        room.idSwitch(),
                        exception.getClass().getSimpleName(),
                        exception.getMessage()
                );
            }
        }

        if (!allConfirmed && !completed.isEmpty()) {
            siteService.confirmShutdowns(
                    snapshot.revision(),
                    completed
            );
        }

        return allConfirmed;
    }

    private boolean belongsToCurrentSite(
            SiteService.Snapshot snapshot,
            SiteInventory.Room previous) {

        return snapshot.inventory().habitaciones().stream()
                .anyMatch(room ->
                        Objects.equals(room.idSwitch(), previous.idSwitch())
                                && Objects.equals(
                                room.urlSwitch().replaceAll("/+$", ""),
                                previous.urlSwitch().replaceAll("/+$", "")
                        )
                );
    }

    private SwitchClient.State commandIfCurrent(
            SiteService.Snapshot snapshot,
            ControlStatus expectedControl,
            SiteInventory.Room room,
            SwitchClient.State desired) {

        synchronized (controlService) {
            ControlStatus current = controlService.status();

            if (current.estado() != ControlStatus.State.EN_EJECUCION
                    || !Objects.equals(
                    current.iniciadoEn(),
                    expectedControl.iniciadoEn())) {

                return null;
            }

            if (desired == SwitchClient.State.ON
                    && shouldStopForPeak(
                    current,
                    controlService.zone(),
                    peakSchedule(snapshot.inventory()))) {

                return null;
            }

            synchronized (siteService) {
                if (!siteService.isCurrent(snapshot.revision())) {
                    return null;
                }

                return switches.command(room, desired);
            }
        }
    }

    private boolean shouldStopForPeak(
            ControlStatus control,
            ZoneId zone,
            PeakSchedule schedule) {

        LocalDateTime localTime =
                LocalDateTime.ofInstant(control.fechaHora(), zone);

        if (schedule.isPeak(localTime)) {
            return true;
        }

        Instant nextPeak = schedule.nextChange(localTime)
                .atZone(zone)
                .toInstant();

        long remainingVirtualMillis = Duration.between(
                control.fechaHora(),
                nextPeak
        ).toMillis();

        long factor = control.factorTiempo() == null
                ? 1000L
                : control.factorTiempo();

        return BigInteger.valueOf(remainingVirtualMillis)
                .multiply(BigInteger.valueOf(1000L))
                .compareTo(
                        BigInteger.valueOf(PEAK_SHUTDOWN_LEAD_MS)
                                .multiply(BigInteger.valueOf(factor))
                ) <= 0;
    }

    private static PeakSchedule peakSchedule(SiteInventory inventory) {
        SiteInventory.PeakPeriod peak =
                inventory.sitio().tarifa().punta();

        return new PeakSchedule(
                LocalTime.parse(peak.desde()),
                LocalTime.parse(peak.hasta()),
                peak.dias()
        );
    }

    private SwitchState switchState(SiteInventory.Room room) {
        try {
            return SwitchState.valueOf(switches.status(room).name());

        } catch (Exception exception) {
            // Sin respuesta, el core trata el estado como desconocido
            // y ordena apagar: no se asume nada sobre el switch.
            log.warn(
                    "No se pudo consultar el switch {} ({}).",
                    room.idSwitch(),
                    exception
            );

            return SwitchState.DESCONOCIDO;
        }
    }

    private void evaluate(
            SiteService.Snapshot snapshot,
            ControlStatus expectedControl) {

        SiteInventory inventory = snapshot.inventory();

        PeakSchedule schedule = peakSchedule(inventory);

        List<RoomConfig> configured = new ArrayList<>();
        List<RoomObservation> observed = new ArrayList<>();

        Map<String, SiteInventory.Room> roomsById = new HashMap<>();

        for (SiteInventory.Room room : inventory.habitaciones()) {
            roomsById.put(room.id(), room);

            configured.add(new RoomConfig(
                    room.id(),
                    room.temperaturaEsperada(),
                    room.potenciaKW()
            ));

            observed.add(new RoomObservation(
                    room.id(),
                    subscriber.temperature(room.topicTermostato()),
                    switchState(room)
            ));
        }

        // La hora virtual se lee DESPUÉS de consultar los switches, que es
        // la parte lenta del ciclo: así la decisión usa la hora más
        // reciente y no la del comienzo del ciclo.
        ControlStatus control;
        ZoneId zone;

        synchronized (controlService) {
            control = controlService.status();
            zone = controlService.zone();
        }

        boolean running =
                control.estado() == ControlStatus.State.EN_EJECUCION;

        if (!running
                || !Objects.equals(
                control.iniciadoEn(),
                expectedControl.iniciadoEn())) {

            return;
        }

        LocalDateTime virtualTime = running
                ? LocalDateTime.ofInstant(control.fechaHora(), zone)
                : null;

        if (!schedule.isPeak(virtualTime)
                && shouldStopForPeak(control, zone, schedule)) {

            virtualTime = schedule.nextChange(virtualTime);
        }

        lastEvaluated = running ? control.fechaHora() : null;

        SiteConfig config = new SiteConfig(
                configured,
                inventory.sitio().potenciaContratadaKW(),
                schedule
        );

        HeatingController.Decision decision = core.decide(
                config,
                virtualTime,
                new ControlState(running, observed)
        );

        execute(
                snapshot,
                control,
                inventory,
                roomsById,
                observed,
                decision
        );
    }

    /**
     * Ejecuta las acciones: primero todos los apagados, después los
     * encendidos. Cada acción se intenta por separado.
     *
     * Si un apagado falla, esa habitación puede seguir consumiendo.
     * Los encendidos solo se hacen si, contando esa potencia como
     * activa, no se supera el máximo del sitio.
     */
    private void execute(
            SiteService.Snapshot snapshot,
            ControlStatus control,
            SiteInventory inventory,
            Map<String, SiteInventory.Room> roomsById,
            List<RoomObservation> observed,
            HeatingController.Decision decision) {

        Set<String> failedOff = new HashSet<>();
        Boolean onAllowed = null;
        int failures = 0;

        for (SwitchAction action : decision.actions()) {
            if (action.state() == SwitchState.ON) {
                // Descartar encendidos si cambió la configuración.
                if (!siteService.isCurrent(snapshot.revision())) {
                    return;
                }

                // Descartar encendidos si se detuvo o resincronizó el control.
                ControlStatus current = controlService.status();

                if (!Objects.equals(
                        current.iniciadoEn(),
                        control.iniciadoEn())
                        || current.estado()
                        != ControlStatus.State.EN_EJECUCION) {

                    return;
                }

                if (onAllowed == null) {
                    onAllowed = fitsMaximumPower(
                            inventory,
                            roomsById,
                            observed,
                            decision,
                            failedOff
                    );
                }

                if (!onAllowed) {
                    log.warn(
                            "Se omite el encendido de {}: un apagado falló "
                                    + "y se superaría la potencia máxima.",
                            action.roomId()
                    );
                    continue;
                }
            }

            SiteInventory.Room room = roomsById.get(action.roomId());

            SwitchClient.State desired =
                    SwitchClient.State.valueOf(action.state().name());

            try {
                SwitchClient.State confirmed =
                        commandIfCurrent(snapshot, control, room, desired);

                if (confirmed == null) {
                    return;
                }

                if (confirmed != desired) {
                    throw new IllegalStateException(
                            "El switch no confirmó " + desired
                    );
                }

                log.info(
                        "Switch {} -> {}",
                        room.idSwitch(),
                        desired
                );

            } catch (Exception exception) {
                failures++;

                if (action.state() == SwitchState.OFF) {
                    failedOff.add(action.roomId());
                }

                log.warn(
                        "No se pudo comandar el switch {} -> {}.",
                        room.idSwitch(),
                        desired,
                        exception
                );
            }
        }

        if (failures > 0) {
            log.warn(
                    "Ciclo de control con {} comando(s) fallido(s). "
                            + "Se reintenta en el próximo ciclo.",
                    failures
            );
        }
    }

    /**
     * Potencia de las habitaciones que quedarían encendidas, más la de
     * las que no se pudieron apagar, contra el máximo del sitio.
     */
    private boolean fitsMaximumPower(
            SiteInventory inventory,
            Map<String, SiteInventory.Room> roomsById,
            List<RoomObservation> observed,
            HeatingController.Decision decision,
            Set<String> failedOff) {

        Set<String> mayBeOn = new HashSet<>();

        for (RoomObservation observation : observed) {
            if (observation.switchState() == SwitchState.ON) {
                mayBeOn.add(observation.id());
            }
        }

        for (SwitchAction action : decision.actions()) {
            if (action.state() == SwitchState.ON) {
                mayBeOn.add(action.roomId());
            } else {
                mayBeOn.remove(action.roomId());
            }
        }

        mayBeOn.addAll(failedOff);

        long totalMilliKw = 0;

        for (String roomId : mayBeOn) {
            totalMilliKw = Math.addExact(
                    totalMilliKw,
                    roomsById.get(roomId).potenciaKW()
            );
        }

        return totalMilliKw
                <= inventory.sitio().potenciaContratadaKW()
                + POWER_TOLERANCE_MILLI_KW;
    }
}