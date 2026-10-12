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

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

            // Procesar los apagados pendientes antes de nuevas decisiones.
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
                    SwitchState.valueOf(switches.status(room).name())
            ));
        }

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
            }

            SiteInventory.Room room =
                    roomsById.get(action.roomId());

            SwitchClient.State desired =
                    SwitchClient.State.valueOf(action.state().name());

            SwitchClient.State confirmed =
                    switches.command(room, desired);

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
        }
    }
}