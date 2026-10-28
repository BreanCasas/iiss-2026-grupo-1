package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.control.ControlCommand;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteRepository;
import com.ioteste.engine.site.SiteService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        room1 = room("room1", "Living", 21500L);
        room2 = room("room2", "Dormitorio", 20000L);

        when(site.confirmShutdowns(1L)).thenReturn(true);
        when(site.isCurrent(1L)).thenReturn(true);
        when(control.zone()).thenReturn(ZoneId.of("America/Montevideo"));

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
        configureSite(3700L);
        setTime("2026-10-05T17:00:00-03:00");
        setTemperatures(19000L, 17000L);
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
        configureSite(1200L);
        setTime("2026-10-05T12:00:00-03:00");

        // Déficit room1 = 1,5; déficit room2 = 3.
        setTemperatures(20000L, 17000L);
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
        configureSite(3700L);
        setTime("2026-10-05T12:00:00-03:00");
        setTemperatures(21500L, 18000L);
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
        configureSite(1200L);
        setTime("2026-10-05T12:00:00-03:00");
        setTemperatures(20000L, 17000L);

        // room2 tiene prioridad y ya está encendida.
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.ON);

        engine.tick();

        verify(switches).status(room1);
        verify(switches).status(room2);
        verify(switches, never()).command(any(), any());
    }

    @Test
    void severalCyclesStopAtPeakAndResumeWithUpdatedPriority() {
        configureSite(1200L);

        // Primer ciclo: room2 tiene mayor déficit.
        setTime("2026-10-05T16:59:00-03:00");
        setTemperatures(20000L, 17000L);
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
        setTime("2026-10-05T17:00:00-03:00");
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
        setTime("2026-10-05T23:00:00-03:00");
        setTemperatures(18000L, 19000L);
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

    @Test
    void unreachableSwitchDoesNotPreventTurningOffTheOtherRoomInPeak() {
        configureSite(3700L);
        setTime("2026-10-05T17:00:00-03:00");
        setTemperatures(19000L, 17000L);

        when(switches.status(room1)).thenThrow(
                new IllegalStateException("sin respuesta"));
        when(switches.status(room2)).thenReturn(SwitchClient.State.ON);

        engine.tick();

        // room2 se apaga aunque no se pudo consultar room1.
        verify(switches).command(room2, SwitchClient.State.OFF);

        // room1 tiene estado desconocido: se le ordena apagar igual.
        verify(switches).command(room1, SwitchClient.State.OFF);

        verify(switches, never()).command(
                any(), eq(SwitchClient.State.ON)
        );
    }

    @Test
    void failedShutdownBlocksTurningOnAnotherRoomWhenPowerWouldBeExceeded() {
        configureSite(1200L);
        setTime("2026-10-05T12:00:00-03:00");

        // room1 debe apagarse y room2 encenderse, pero room1 no responde.
        setTemperatures(20000L, 17000L);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        when(switches.command(room1, SwitchClient.State.OFF)).thenThrow(
                new IllegalStateException("sin respuesta"));

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.OFF);

        // room1 puede seguir encendida (1,2 kW): encender room2
        // superaría el máximo de 1,2 kW.
        verify(switches, never()).command(
                room2, SwitchClient.State.ON
        );
    }

    @Test
    void failedShutdownDoesNotBlockTurningOnWhenThereIsEnoughPower() {
        configureSite(3700L);
        setTime("2026-10-05T12:00:00-03:00");

        // room1 llegó al objetivo (debe apagarse); room2 está fría.
        setTemperatures(21500L, 17000L);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        when(switches.command(room1, SwitchClient.State.OFF)).thenThrow(
                new IllegalStateException("sin respuesta"));

        engine.tick();

        // 1,2 kW (room1, posiblemente encendida) + 1,2 kW (room2) <= 3,7 kW.
        verify(switches).command(room2, SwitchClient.State.ON);
    }

    @Test
    void nextDelayWakesUpAtThePeakStartWhenTheClockIsAccelerated() {
        configureSite(3700L);

        // Faltan 30 s virtuales para las 17:00; a factor 60, 0,5 s reales.
        setTime("2026-10-05T16:59:30-03:00", 60000L);

        long millis = engine.nextDelay().toMillis();

        assertTrue(millis >= 500 && millis <= 502,
                "Debe despertar a los ~500 ms: " + millis);
    }

    @Test
    void nextDelayIsTheRegularPeriodWhenNoSiteIsLoaded() {
        when(site.get()).thenThrow(new IllegalStateException("sin sitio"));

        assertEquals(HeatingEngine.REGULAR_PERIOD, engine.nextDelay());
    }

    @Test
    void nextDelayIsTheRegularPeriodWhenControlIsStopped() {
        configureSite(3700L);
        when(control.status()).thenReturn(ControlStatus.stopped());

        assertEquals(HeatingEngine.REGULAR_PERIOD, engine.nextDelay());
    }

    @Test
    void expiredTemperatureTurnsHeatingOffAndFreshReadingRestartsIt() {
        configureSite(3700L);
        setTime("2026-10-05T12:00:00-03:00");

        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);

        TemperatureSubscriber subscriber = new TemperatureSubscriber(
                mock(EnvironmentRegistration.class),
                site,
                new ObjectMapper(),
                mock(TemperatureReadingRepository.class),
                clock
        );

        ReflectionTestUtils.setField(
                subscriber,
                "activeTopics",
                Set.of(room1.topicTermostato())
        );

        engine = new HeatingEngine(site, control, subscriber, switches);

        MqttMessage message = new MqttMessage(
                "{\"tC\":18.0}".getBytes(StandardCharsets.UTF_8)
        );

        subscriber.receive(room1.topicTermostato(), message);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.ON);
        clearInvocations(switches);

        when(clock.millis()).thenReturn(31_000L);
        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.OFF);
        verify(switches, never()).command(any(), eq(SwitchClient.State.ON));
        clearInvocations(switches);

        subscriber.receive(room1.topicTermostato(), message);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.ON);
    }

    @Test
    void startingThenStoppingControlTurnsHeatingOnThenOff() {
        configureSite(3700L);
        useRealServices();

        setTemperatures(19000L, 21000L);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        control.command(new ControlCommand(
                "INICIAR",
                OffsetDateTime.parse("2026-10-05T12:00:00-03:00"),
                1000L
        ));

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.ON);
        verify(switches, never()).command(room2, SwitchClient.State.ON);
        clearInvocations(switches);

        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);

        control.command(new ControlCommand("DETENER", null, null));

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.OFF);
        verify(switches, never()).command(any(), eq(SwitchClient.State.ON));
    }

    @Test
    void changingTargetTemperatureWhileRunningTurnsHeatingOff() {
        configureSite(3700L);
        useRealServices();

        setTemperatures(19000L, 21000L);
        setSwitchStates(SwitchClient.State.OFF, SwitchClient.State.OFF);

        control.command(new ControlCommand(
                "INICIAR",
                OffsetDateTime.parse("2026-10-05T12:00:00-03:00"),
                1000L
        ));

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.ON);
        clearInvocations(switches);

        SiteInventory.Room updatedRoom = new SiteInventory.Room(
                room1.id(),
                room1.nombre(),
                18000L,
                room1.potenciaKW(),
                room1.idTermostato(),
                room1.topicTermostato(),
                room1.idSwitch(),
                room1.urlSwitch()
        );

        SiteInventory previous = site.get();

        site.replace(new SiteInventory(
                previous.schemaVersion(),
                previous.sitio(),
                List.of(updatedRoom, room2)
        ));

        setSwitchStates(SwitchClient.State.ON, SwitchClient.State.OFF);
        when(switches.status(updatedRoom)).thenReturn(SwitchClient.State.OFF);

        engine.tick();

        verify(switches).command(room1, SwitchClient.State.OFF);
        verify(switches).status(updatedRoom);
        verify(switches, never()).command(any(), eq(SwitchClient.State.ON));

        assertEquals(
                ControlStatus.State.EN_EJECUCION,
                control.status().estado()
        );
        assertEquals(
                18000L,
                site.get().habitaciones().get(0).temperaturaEsperada()
        );
        assertTrue(site.snapshot().pendingShutdowns().isEmpty());
    }

    private void useRealServices() {
        SiteInventory inventory = site.get();
        SiteRepository repository = mock(SiteRepository.class);

        when(repository.load()).thenReturn(Optional.of(inventory));
        when(repository.loadPendingShutdowns()).thenReturn(List.of());

        site = new SiteService(repository);
        control = new ControlService();
        engine = new HeatingEngine(site, control, temperatures, switches);
    }

    private void configureSite(long maximumPowerKw) {
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

        when(site.get()).thenReturn(inventory);
    }

    private void setTime(String virtualTime) {
        setTime(virtualTime, 1000L);
    }

    private void setTime(String virtualTime, long factor) {
        when(control.status()).thenReturn(
                new ControlStatus(
                        ControlStatus.State.EN_EJECUCION,
                        OffsetDateTime.parse(virtualTime).toInstant(),
                        factor,
                        Instant.parse("2026-10-05T10:00:00Z")
                )
        );
    }

    private void setTemperatures(long first, long second) {
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
            String id, String name, long target) {

        return new SiteInventory.Room(
                id,
                name,
                target,
                1200L,
                "ht-" + id,
                "ht-" + id + "/status/temperature:0",
                "sw-" + id,
                "http://localhost:9090"
        );
    }
}