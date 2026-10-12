package com.ioteste.engine.integration;

import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeatingScenarioTest {

    private SiteService site;
    private ControlService control;
    private TemperatureSubscriber temperatures;
    private SwitchClient switches;
    private HeatingEngine engine;

    private SiteInventory.Room room1;
    private SiteInventory.Room room2;

    @BeforeEach
    void prepareScenario() {
        site = mock(SiteService.class);
        control = mock(ControlService.class);
        temperatures = mock(TemperatureSubscriber.class);
        switches = mock(SwitchClient.class);

        room1 = room("room1", "Living", 21.5);
        room2 = room("room2", "Dormitorio", 20.0);

        when(site.confirmShutdowns(1L)).thenReturn(true);
        when(site.isCurrent(1L)).thenReturn(true);
        when(control.timeOffset()).thenReturn(ZoneOffset.UTC);

        // El dispositivo simulado confirma el estado solicitado.
        when(switches.command(any(), any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(1, SwitchClient.State.class));

        engine = new HeatingEngine(
                site, control, temperatures, switches
        );
    }

    @Test
    void enteringPeakTurnsOffBothColdRooms() {
        configureSite(3.7);
        setTime("2026-10-05T17:00:00Z");
        setTemperatures(19.0, 17.0);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.ON);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.OFF);
        verify(switches).command(room2, SwitchClient.State.OFF);
        verify(switches, never()).command(
                any(), eq(SwitchClient.State.ON)
        );
    }

    @Test
    void limitedPowerTurnsOffLowerPriorityBeforeHeatingHigherPriority() {
        configureSite(1.2);
        setTime("2026-10-05T12:00:00Z");

        // Déficit room1 = 1,5; déficit room2 = 3.
        setTemperatures(20.0, 17.0);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        engine.tick();

        InOrder order = inOrder(switches);
        order.verify(switches).command(room1, SwitchClient.State.OFF);
        order.verify(switches).command(room2, SwitchClient.State.ON);

        verify(switches, never()).command(
                room1, SwitchClient.State.ON
        );
        verify(switches, never()).command(
                room2, SwitchClient.State.OFF
        );
    }

    @Test
    void reachingTargetTurnsOffOneRoomAndStartsTheOther() {
        configureSite(3.7);
        setTime("2026-10-05T12:00:00Z");
        setTemperatures(21.5, 18.0);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        engine.tick();

        InOrder order = inOrder(switches);
        order.verify(switches).command(room1, SwitchClient.State.OFF);
        order.verify(switches).command(room2, SwitchClient.State.ON);

        verify(switches, never()).command(
                room1, SwitchClient.State.ON
        );
        verify(switches, never()).command(
                room2, SwitchClient.State.OFF
        );
    }

    @Test
    void correctSwitchStatesDoNotProduceRedundantCommands() {
        configureSite(1.2);
        setTime("2026-10-05T12:00:00Z");
        setTemperatures(20.0, 17.0);

        // room2 tiene prioridad y ya está encendida.
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.ON);

        engine.tick();

        verify(switches).status(room1);
        verify(switches).status(room2);
        verify(switches, never()).command(any(), any());
    }

    @Test
    void severalCyclesStopAtPeakAndResumeWithUpdatedPriority() {
        configureSite(1.2);

        // Primer ciclo: room2 tiene mayor déficit.
        setTime("2026-10-05T16:59:00Z");
        setTemperatures(20.0, 17.0);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room2, SwitchClient.State.ON);
        verify(switches, never()).command(
                room1, SwitchClient.State.ON
        );
        verify(switches, never()).command(
                any(), eq(SwitchClient.State.OFF)
        );

        // Limpiar solo las llamadas registradas, no la configuración.
        clearInvocations(switches);

        // Segundo ciclo: entra en punta con room2 encendida.
        setTime("2026-10-05T17:00:00Z");
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.ON);

        engine.tick();

        verify(switches).command(room2, SwitchClient.State.OFF);
        verify(switches, never()).command(
                room1, SwitchClient.State.OFF
        );
        verify(switches, never()).command(
                any(), eq(SwitchClient.State.ON)
        );

        clearInvocations(switches);

        // Tercer ciclo: sale de punta y las temperaturas cambiaron.
        // Ahora room1 tiene mayor déficit: 3,5 frente a 1.
        setTime("2026-10-05T23:00:00Z");
        setTemperatures(18.0, 19.0);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.ON);
        verify(switches, never()).command(
                room2, SwitchClient.State.ON
        );
        verify(switches, never()).command(
                any(), eq(SwitchClient.State.OFF)
        );
    }

    private void configureSite(double maximumPowerKw) {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                new SiteInventory.Site(
                        "casa",
                        "Casa",
                        maximumPowerKw,
                        new SiteInventory.Tariff(
                                new SiteInventory.PeakPeriod(
                                        "17:00",
                                        "23:00",
                                        PeakSchedule.Days.HABILES
                                )
                        )
                ),
                List.of(room1, room2)
        );

        when(site.snapshot()).thenReturn(
                new SiteService.Snapshot(inventory, List.of(), 1L)
        );
    }

    private void setTime(String virtualTime) {
        when(control.status()).thenReturn(
                new ControlStatus(
                        ControlStatus.State.EN_EJECUCION,
                        Instant.parse(virtualTime),
                        1.0,
                        Instant.parse("2026-10-05T10:00:00Z")
                )
        );
    }

    private void setTemperatures(double first, double second) {
        when(temperatures.temperature(room1.topicTermostato()))
                .thenReturn(first);
        when(temperatures.temperature(room2.topicTermostato()))
                .thenReturn(second);
    }

    private void setSwitchStates(
            SwitchClient.State first,
            SwitchClient.State second) {

        when(switches.status(room1)).thenReturn(first);
        when(switches.status(room2)).thenReturn(second);
    }

    private SiteInventory.Room room(
            String id, String name, double target) {

        return new SiteInventory.Room(
                id,
                name,
                target,
                1.2,
                "ht-" + id,
                "ht-" + id + "/status/temperature:0",
                "sw-" + id,
                "http://localhost:9090"
        );
    }
}