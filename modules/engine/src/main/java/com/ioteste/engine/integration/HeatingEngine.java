package com.ioteste.engine.integration;

import com.ioteste.core.HeatingController;
import com.ioteste.core.PeakSchedule;
import com.ioteste.core.RoomState;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeatingEngine {

    private static final Logger log =
            LoggerFactory.getLogger(HeatingEngine.class);

    private final SiteService siteService;
    private final ControlService controlService;
    private final TemperatureSubscriber subscriber;
    private final SwitchClient switches;

    private final HeatingController core = new HeatingController();

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

    @Scheduled(initialDelay = 5000, fixedDelay = 1000)
    public synchronized void tick() {
        try {
            SiteService.Snapshot snapshot = siteService.snapshot();

            // Las llamadas HTTP no mantienen bloqueado SiteService.
            for (SiteInventory.Room room : snapshot.pendingShutdowns()) {
                SwitchClient.State confirmed = switches.command(
                        room,
                        SwitchClient.State.OFF
                );

                if (confirmed != SwitchClient.State.OFF) {
                    throw new IllegalStateException(
                            "El switch no confirmó OFF"
                    );
                }

                log.info("Switch anterior {} -> OFF", room.idSwitch());
            }

            // Si hubo otra actualización durante los apagados,
            // conservar sus pendientes para el próximo ciclo.
            if (!siteService.confirmShutdowns(snapshot.revision())) {
                return;
            }

            ControlStatus control;
            ZoneOffset offset;

            synchronized (controlService) {
                control = controlService.status();
                offset = controlService.timeOffset();
            }

            evaluate(snapshot, control, offset);
        } catch (Exception exception) {
            log.warn(
                    "Ciclo de control interrumpido ({}): {}. Reintentando.",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }

    private void evaluate(
            SiteService.Snapshot snapshot,
            ControlStatus control,
            ZoneOffset offset) {

        SiteInventory inventory = snapshot.inventory();

        SiteInventory.PeakPeriod peak =
                inventory.sitio().tarifa().punta();

        PeakSchedule schedule = new PeakSchedule(
                LocalTime.parse(peak.desde()),
                LocalTime.parse(peak.hasta()),
                peak.dias()
        );

        boolean running =
                control.estado() == ControlStatus.State.EN_EJECUCION;

        LocalDateTime virtualTime = running
                ? LocalDateTime.ofInstant(control.fechaHora(), offset)
                : null;

        List<RoomState> measuredRooms = new ArrayList<>();

        for (SiteInventory.Room room : inventory.habitaciones()) {
            Double temperature = subscriber.temperature(
                    room.topicTermostato()
            );

            if (temperature != null) {
                measuredRooms.add(new RoomState(
                        room.id(),
                        temperature,
                        room.temperaturaEsperada(),
                        room.potenciaKW()
                ));
            }
        }

        Set<String> selected = !running
                ? Set.of()
                : core.selectRoomsToHeat(
                measuredRooms,
                inventory.sitio().potenciaContratadaKW(),
                schedule.isPeak(virtualTime)
        );

        Map<String, SwitchClient.State> states = new HashMap<>();

        for (SiteInventory.Room room : inventory.habitaciones()) {
            states.put(room.id(), switches.status(room));
        }

        // Primero confirmar los apagados necesarios.
        for (SiteInventory.Room room : inventory.habitaciones()) {
            SwitchClient.State state = states.get(room.id());

            if (state == SwitchClient.State.DESCONOCIDO
                    || (!selected.contains(room.id())
                    && state != SwitchClient.State.OFF)) {

                states.put(
                        room.id(),
                        switches.command(room, SwitchClient.State.OFF)
                );

                log.info("Switch {} -> OFF", room.idSwitch());
            }
        }

        // Descartar encendidos de un ciclo cuya configuración
        // o estado de ejecución haya cambiado.
        for (SiteInventory.Room room : inventory.habitaciones()) {
            if (!siteService.isCurrent(snapshot.revision())) {
                return;
            }

            ControlStatus current = controlService.status();

            if (!Objects.equals(
                    current.iniciadoEn(),
                    control.iniciadoEn())
                    || current.estado()
                    != ControlStatus.State.EN_EJECUCION) {

                return;
            }

            if (selected.contains(room.id())
                    && states.get(room.id()) != SwitchClient.State.ON) {

                switches.command(room, SwitchClient.State.ON);
                log.info("Switch {} -> ON", room.idSwitch());
            }
        }
    }
}