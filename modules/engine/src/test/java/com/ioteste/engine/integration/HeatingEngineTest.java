package com.ioteste.engine.integration;

import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.control.ControlCommand;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteRepository;
import com.ioteste.engine.site.SiteService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeatingEngineTest {

    @Test
    void stoppedControlDoesNotSendCommandsOrClearPendingShutdowns() {
        SiteInventory.Room room = room("room1");
        Scenario scenario = scenario(List.of(room), List.of(room));

        scenario.control().command(
                new ControlCommand("DETENER", null, null));

        scenario.engine().tick();

        verifyNoInteractions(scenario.switches(), scenario.subscriber());
        verify(scenario.repository(), never()).save(any(), anyList());

        assertEquals(
                List.of(room),
                scenario.site().snapshot().pendingShutdowns());
    }

    @Test
    void failedShutdownOfCurrentSwitchKeepsPendingWorkAndPreventsEvaluation() {
        SiteInventory.Room room = room("room1");
        Scenario scenario = scenario(List.of(room), List.of(room));

        when(scenario.switches().command(room, SwitchClient.State.OFF))
                .thenThrow(new ResourceAccessException("Sin conexión"));

        scenario.engine().tick();

        assertEquals(
                List.of(room),
                scenario.site().snapshot().pendingShutdowns());

        verify(scenario.repository(), never()).save(any(), anyList());
        verifyNoInteractions(scenario.subscriber());
        verify(scenario.switches(), never()).status(any());

        verify(scenario.switches(), never()).command(
                any(), eq(SwitchClient.State.ON));
    }

    @Test
    void confirmedShutdownIsNotRepeatedWhenAnotherCurrentSwitchKeepsFailing() {
        SiteInventory.Room first = room("room1");
        SiteInventory.Room second = room("room2");

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(first, second));

        when(scenario.switches().command(second, SwitchClient.State.OFF))
                .thenThrow(new ResourceAccessException("Sin conexión"));

        scenario.engine().tick();

        assertEquals(
                List.of(second),
                scenario.site().snapshot().pendingShutdowns());

        verify(scenario.repository()).save(
                scenario.site().get(), List.of(second));

        scenario.engine().tick();

        verify(scenario.switches(), times(1))
                .command(first, SwitchClient.State.OFF);

        verify(scenario.switches(), times(2))
                .command(second, SwitchClient.State.OFF);

        verifyNoInteractions(scenario.subscriber());
    }

    @Test
    void pendingSwitchOutsideCurrentSiteIsDiscardedWithoutContactingIt() {
        SiteInventory.Room removed = room("room4");
        SiteInventory.Room current = room("room1");

        Scenario scenario = scenario(
                List.of(removed), List.of(current));

        scenario.engine().tick();

        assertTrue(scenario.site().snapshot().pendingShutdowns().isEmpty());

        verify(scenario.switches(), never()).command(eq(removed), any());
        verify(scenario.switches(), never()).status(removed);
        verify(scenario.switches()).status(current);

        verify(scenario.repository()).save(
                scenario.site().get(), List.of());
    }

    @Test
    void matchingSwitchIdAtAnotherAddressDoesNotAuthorizeOldEndpoint() {
        SiteInventory.Room previous = room("room1");
        SiteInventory.Room current = withUrl(
                previous, "http://localhost:9091");

        Scenario scenario = scenario(
                List.of(previous), List.of(current));

        scenario.engine().tick();

        assertTrue(scenario.site().snapshot().pendingShutdowns().isEmpty());

        verify(scenario.switches(), never()).command(eq(previous), any());
        verify(scenario.switches()).status(current);
    }

    @Test
    void trailingSlashDoesNotMakeCurrentSwitchLookRemoved() {
        SiteInventory.Room previous = room("room1");
        SiteInventory.Room current = withUrl(
                previous, previous.urlSwitch() + "/");

        Scenario scenario = scenario(
                List.of(previous), List.of(current));

        scenario.engine().tick();

        verify(scenario.switches()).command(
                previous, SwitchClient.State.OFF);

        assertTrue(scenario.site().snapshot().pendingShutdowns().isEmpty());
    }

    @Test
    void missingSwitchStillInCurrentSiteIsNotAssumedOff() {
        SiteInventory.Room room = room("room1");
        Scenario scenario = scenario(List.of(room), List.of(room));

        when(scenario.switches().command(room, SwitchClient.State.OFF))
                .thenThrow(
                        new HttpClientErrorException(HttpStatus.NOT_FOUND));

        scenario.engine().tick();

        assertEquals(
                List.of(room),
                scenario.site().snapshot().pendingShutdowns());

        verify(scenario.repository(), never()).save(any(), anyList());
        verifyNoInteractions(scenario.subscriber());
    }

    @Test
    void stoppingDuringPendingShutdownsPreventsTheNextCommand() {
        SiteInventory.Room first = room("room1");
        SiteInventory.Room second = room("room2");

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(first, second));

        when(scenario.switches().command(first, SwitchClient.State.OFF))
                .thenAnswer(invocation -> {
                    scenario.control().command(
                            new ControlCommand("DETENER", null, null));
                    return SwitchClient.State.OFF;
                });

        scenario.engine().tick();

        verify(scenario.switches()).command(
                first, SwitchClient.State.OFF);

        verify(scenario.switches(), never()).command(eq(second), any());

        assertEquals(
                List.of(second),
                scenario.site().snapshot().pendingShutdowns());
    }

    @Test
    void inventoryChangeDuringPendingShutdownsPreventsStaleCommands() {
        SiteInventory.Room first = room("room1");
        SiteInventory.Room second = room("room2");

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(first, second));

        when(scenario.switches().command(first, SwitchClient.State.OFF))
                .thenAnswer(invocation -> {
                    SiteInventory current = scenario.site().get();

                    scenario.site().replace(new SiteInventory(
                            current.schemaVersion(),
                            current.sitio(),
                            List.of(first)));

                    return SwitchClient.State.OFF;
                });

        scenario.engine().tick();

        verify(scenario.switches(), never()).command(eq(second), any());

        assertEquals(
                List.of(first),
                scenario.site().get().habitaciones());
    }

    @Test
    void failedPersistenceKeepsConfirmedShutdownPendingForRetry() {
        SiteInventory.Room first = room("room1");
        SiteInventory.Room second = room("room2");

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(first, second));

        when(scenario.switches().command(second, SwitchClient.State.OFF))
                .thenThrow(new ResourceAccessException("Sin conexión"));

        doThrow(new IllegalStateException("Mongo inaccesible"))
                .when(scenario.repository()).save(any(), anyList());

        scenario.engine().tick();

        assertEquals(
                List.of(first, second),
                scenario.site().snapshot().pendingShutdowns());

        verify(scenario.repository()).save(
                scenario.site().get(), List.of(second));

        verifyNoInteractions(scenario.subscriber());
    }

    @Test
    void oldRevisionCannotClearPendingShutdownsAfterInventoryChange() {
        SiteInventory.Room first = room("room1");
        SiteInventory.Room second = room("room2");

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(first, second));

        long previousRevision = scenario.site().snapshot().revision();
        SiteInventory current = scenario.site().get();

        scenario.site().replace(new SiteInventory(
                current.schemaVersion(),
                current.sitio(),
                List.of(first)));

        clearInvocations(scenario.repository());

        assertFalse(scenario.site().confirmShutdowns(
                previousRevision, List.of(first)));

        verify(scenario.repository(), never()).save(any(), anyList());
    }

    @Test
    void approachingPeakTurnsOffBeforeTheBoundaryAtFactorSixty() {
        SwitchClient switches = tickAt(
                "2026-11-03T16:59:30-03:00",
                60000L,
                SwitchClient.State.ON);

        verify(switches).command(room("room1"), SwitchClient.State.OFF);

        verify(switches, never()).command(
                any(), eq(SwitchClient.State.ON));
    }

    @Test
    void approachingPeakDoesNotStartAnotherHeater() {
        SwitchClient switches = tickAt(
                "2026-11-03T16:59:30-03:00",
                60000L,
                SwitchClient.State.OFF);

        verify(switches, never()).command(
                any(), eq(SwitchClient.State.ON));
    }

    @Test
    void coldRoomStillHeatsOutsideTheShutdownMargin() {
        SwitchClient switches = tickAt(
                "2026-11-03T16:57:00-03:00",
                60000L,
                SwitchClient.State.OFF);

        verify(switches).command(room("room1"), SwitchClient.State.ON);
    }

    @Test
    void weekdayPeakDoesNotPreventHeatingOnSaturday() {
        SwitchClient switches = tickAt(
                "2026-11-07T16:59:30-03:00",
                60000L,
                SwitchClient.State.OFF);

        verify(switches).command(room("room1"), SwitchClient.State.ON);
    }

    @Test
    void leavingPeakAllowsHeatingWithoutAnExtraMargin() {
        SwitchClient switches = tickAt(
                "2026-11-03T23:00:00-03:00",
                60000L,
                SwitchClient.State.OFF);

        verify(switches).command(room("room1"), SwitchClient.State.ON);
    }

    @Test
    void normalSpeedUsesTwoSecondsInsteadOfTwoMinutes() {
        SwitchClient switches = tickAt(
                "2026-11-03T16:59:30-03:00",
                1000L,
                SwitchClient.State.OFF);

        verify(switches).command(room("room1"), SwitchClient.State.ON);
    }

    @Test
    void normalSpeedTurnsOffWithinTwoSecondsOfPeakStart() {
        SwitchClient switches = tickAt(
                "2026-11-03T16:59:59-03:00",
                1000L,
                SwitchClient.State.ON);

        verify(switches).command(room("room1"), SwitchClient.State.OFF);
    }

    @Test
    void enteringShutdownMarginDuringTheCycleCancelsAnOutdatedOnDecision() {
        SiteInventory.Room room = room("room1");
        Scenario scenario = scenario(List.of(), List.of(room));
        ControlService control = mock(ControlService.class);

        Instant started = Instant.parse("2026-11-07T23:00:00Z");

        ControlStatus before = new ControlStatus(
                ControlStatus.State.EN_EJECUCION,
                OffsetDateTime.parse(
                        "2026-11-03T16:57:00-03:00").toInstant(),
                60000L,
                started);

        ControlStatus nearPeak = new ControlStatus(
                ControlStatus.State.EN_EJECUCION,
                OffsetDateTime.parse(
                        "2026-11-03T16:59:30-03:00").toInstant(),
                60000L,
                started);

        when(control.zone()).thenReturn(
                ZoneId.of("America/Montevideo"));

        when(control.status()).thenReturn(
                before, before, nearPeak, nearPeak);

        when(scenario.subscriber().temperature(room.topicTermostato()))
                .thenReturn(19000L);

        new HeatingEngine(
                scenario.site(),
                control,
                scenario.subscriber(),
                scenario.switches()
        ).tick();

        verify(scenario.switches(), never()).command(
                any(), eq(SwitchClient.State.ON));
    }

    private SwitchClient tickAt(
            String virtualTime,
            long factor,
            SwitchClient.State state) {

        SiteInventory.Room room = room("room1");
        Scenario scenario = scenario(List.of(), List.of(room));
        ControlService control = mock(ControlService.class);

        when(control.zone()).thenReturn(
                ZoneId.of("America/Montevideo"));

        when(control.status()).thenReturn(new ControlStatus(
                ControlStatus.State.EN_EJECUCION,
                OffsetDateTime.parse(virtualTime).toInstant(),
                factor,
                Instant.parse("2026-11-07T23:00:00Z")));

        when(scenario.switches().status(room)).thenReturn(state);

        when(scenario.subscriber().temperature(room.topicTermostato()))
                .thenReturn(19000L);

        new HeatingEngine(
                scenario.site(),
                control,
                scenario.subscriber(),
                scenario.switches()
        ).tick();

        return scenario.switches();
    }

    private Scenario scenario(
            List<SiteInventory.Room> pending,
            List<SiteInventory.Room> configured) {

        SiteInventory inventory = new SiteInventory(
                "2.0",
                new SiteInventory.Site(
                        "casa",
                        "Casa",
                        3700L,
                        new SiteInventory.Tariff(
                                new SiteInventory.PeakPeriod(
                                        "17:00",
                                        "23:00",
                                        PeakSchedule.Days.HABILES))),
                configured);

        SiteRepository repository = mock(SiteRepository.class);

        when(repository.load()).thenReturn(Optional.of(inventory));
        when(repository.loadPendingShutdowns()).thenReturn(pending);

        SiteService site = new SiteService(repository);
        ControlService control = new ControlService();

        control.command(new ControlCommand(
                "INICIAR",
                OffsetDateTime.parse("2026-11-03T12:00:00-03:00"),
                1000L));

        TemperatureSubscriber subscriber =
                mock(TemperatureSubscriber.class);

        SwitchClient switches = mock(SwitchClient.class);

        when(switches.status(any())).thenReturn(SwitchClient.State.OFF);

        when(switches.command(any(), any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(1, SwitchClient.State.class));

        HeatingEngine engine = new HeatingEngine(
                site, control, subscriber, switches);

        return new Scenario(
                repository, site, control, subscriber, switches, engine);
    }

    private SiteInventory.Room room(String id) {
        return new SiteInventory.Room(
                id,
                id,
                21500L,
                1200L,
                "ht-" + id,
                "ht-" + id + "/status/temperature:0",
                "sw-" + id,
                "http://localhost:9090");
    }

    private SiteInventory.Room withUrl(
            SiteInventory.Room room,
            String url) {

        return new SiteInventory.Room(
                room.id(),
                room.nombre(),
                room.temperaturaEsperada(),
                room.potenciaKW(),
                room.idTermostato(),
                room.topicTermostato(),
                room.idSwitch(),
                url);
    }

    private record Scenario(
            SiteRepository repository,
            SiteService site,
            ControlService control,
            TemperatureSubscriber subscriber,
            SwitchClient switches,
            HeatingEngine engine) {
    }
}