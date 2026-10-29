package com.ioteste.engine.integration;

import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteRepository;
import com.ioteste.engine.site.SiteService;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeatingEngineTest {

    private SiteInventory.Room room() {
        return new SiteInventory.Room(
                "room1",
                "Living",
                21500L,
                1200L,
                "ht-room1",
                "ht-room1/status/temperature:0",
                "sw-room1",
                "http://localhost:9090"
        );
    }

    private SiteInventory inventory() {
        return new SiteInventory(
                "2.0",
                new SiteInventory.Site(
                        "casa",
                        "Casa",
                        3700L,
                        new SiteInventory.Tariff(
                                new SiteInventory.PeakPeriod(
                                        "17:00",
                                        "23:00",
                                        PeakSchedule.Days.HABILES
                                )
                        )
                ),
                List.of()
        );
    }

    @Test
    void failedShutdownKeepsPendingWorkAndPreventsEvaluation() {
        SiteService site = mock(SiteService.class);
        ControlService control = mock(ControlService.class);
        TemperatureSubscriber subscriber =
                mock(TemperatureSubscriber.class);
        SwitchClient switches = mock(SwitchClient.class);

        when(site.snapshot()).thenReturn(
                new SiteService.Snapshot(
                        inventory(),
                        List.of(room()),
                        1
                )
        );

        when(switches.command(room(), SwitchClient.State.OFF))
                .thenThrow(
                        new IllegalStateException("Switch inaccesible")
                );

        new HeatingEngine(
                site,
                control,
                subscriber,
                switches
        ).tick();

        verify(site, never()).confirmShutdowns(anyLong());
        verifyNoInteractions(control, subscriber);

        verify(switches, never()).command(
                any(),
                eq(SwitchClient.State.ON)
        );
    }

    @Test
    void oneFailedPendingShutdownDoesNotPreventShuttingDownTheOthers() {
        SiteService site = mock(SiteService.class);
        ControlService control = mock(ControlService.class);
        TemperatureSubscriber subscriber =
                mock(TemperatureSubscriber.class);
        SwitchClient switches = mock(SwitchClient.class);

        SiteInventory.Room other = new SiteInventory.Room(
                "room2",
                "Dormitorio",
                20000L,
                800L,
                "ht-room2",
                "ht-room2/status/temperature:0",
                "sw-room2",
                "http://localhost:9090"
        );

        when(site.snapshot()).thenReturn(
                new SiteService.Snapshot(
                        inventory(),
                        List.of(room(), other),
                        1
                )
        );

        when(switches.command(room(), SwitchClient.State.OFF))
                .thenThrow(new IllegalStateException("Switch inaccesible"));

        when(switches.command(other, SwitchClient.State.OFF))
                .thenReturn(SwitchClient.State.OFF);

        new HeatingEngine(
                site,
                control,
                subscriber,
                switches
        ).tick();

        // El segundo switch se apaga aunque el primero falló...
        verify(switches).command(other, SwitchClient.State.OFF);

        // ...pero el trabajo pendiente se conserva para reintentar.
        verify(site, never()).confirmShutdowns(anyLong());
        verifyNoInteractions(control, subscriber);
    }

    @Test
    void processesPendingShutdownsEvenWhenControlIsStopped() {
        SiteService site = mock(SiteService.class);
        ControlService control = mock(ControlService.class);
        TemperatureSubscriber subscriber =
                mock(TemperatureSubscriber.class);
        SwitchClient switches = mock(SwitchClient.class);

        when(site.snapshot()).thenReturn(
                new SiteService.Snapshot(
                        inventory(),
                        List.of(room()),
                        1
                )
        );

        when(switches.command(room(), SwitchClient.State.OFF))
                .thenReturn(SwitchClient.State.OFF);

        when(site.confirmShutdowns(1)).thenReturn(true);
        when(control.status()).thenReturn(ControlStatus.stopped());

        new HeatingEngine(
                site,
                control,
                subscriber,
                switches
        ).tick();

        var order = inOrder(switches, site);

        order.verify(switches).command(
                room(),
                SwitchClient.State.OFF
        );

        order.verify(site).confirmShutdowns(1);

        verify(switches, never()).command(
                any(),
                eq(SwitchClient.State.ON)
        );
    }

    @Test
    void confirmedShutdownIsNotRepeatedWhenAnotherSwitchKeepsFailing() {
        SiteInventory.Room first = room();
        SiteInventory.Room second = otherRoom();

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(),
                false
        );

        when(scenario.switches().command(second, SwitchClient.State.OFF))
                .thenThrow(new ResourceAccessException("Sin conexión"));

        scenario.engine().tick();

        assertEquals(
                List.of(second),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository()).save(
                scenario.site().get(),
                List.of(second)
        );

        scenario.engine().tick();

        verify(scenario.switches(), times(1))
                .command(first, SwitchClient.State.OFF);

        verify(scenario.switches(), times(2))
                .command(second, SwitchClient.State.OFF);

        verifyNoInteractions(scenario.control());
    }

    @Test
    void explicitlyMissingRemovedSwitchIsDiscardedWhenEnabled() {
        Scenario scenario = scenario(
                List.of(room()),
                List.of(),
                true
        );

        when(scenario.switches().command(room(), SwitchClient.State.OFF))
                .thenThrow(notFound("no existe el switch sw-room1"));

        scenario.engine().tick();

        assertTrue(scenario.site().snapshot().pendingShutdowns().isEmpty());

        verify(scenario.repository()).save(
                scenario.site().get(),
                List.of()
        );

        verify(scenario.control()).status();

        verify(scenario.switches(), never()).command(
                any(),
                eq(SwitchClient.State.ON)
        );
    }

    @Test
    void missingRemovedSwitchIsRetriedWhenOptionIsDisabled() {
        Scenario scenario = scenario(
                List.of(room()),
                List.of(),
                false
        );

        when(scenario.switches().command(room(), SwitchClient.State.OFF))
                .thenThrow(notFound("no existe el switch sw-room1"));

        scenario.engine().tick();

        assertEquals(
                List.of(room()),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository(), never()).save(any(), anyList());
        verifyNoInteractions(scenario.control());
    }

    @Test
    void missingSwitchStillInCurrentInventoryIsNotDiscarded() {
        Scenario scenario = scenario(
                List.of(room()),
                List.of(room()),
                true
        );

        when(scenario.switches().command(room(), SwitchClient.State.OFF))
                .thenThrow(notFound("no existe el switch sw-room1"));

        scenario.engine().tick();

        assertEquals(
                List.of(room()),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository(), never()).save(any(), anyList());
        verifyNoInteractions(scenario.control());
    }

    @Test
    void genericNotFoundResponseDoesNotDiscardPendingShutdown() {
        Scenario scenario = scenario(
                List.of(room()),
                List.of(),
                true
        );

        when(scenario.switches().command(room(), SwitchClient.State.OFF))
                .thenThrow(notFound("ruta no encontrada"));

        scenario.engine().tick();

        assertEquals(
                List.of(room()),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository(), never()).save(any(), anyList());
        verifyNoInteractions(scenario.control());
    }

    @Test
    void communicationAuthenticationAndServerErrorsKeepPendingShutdown() {
        List<RuntimeException> failures = List.of(
                new ResourceAccessException("Tiempo de espera agotado"),
                HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        new HttpHeaders(),
                        new byte[0],
                        StandardCharsets.UTF_8
                ),
                HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error",
                        new HttpHeaders(),
                        new byte[0],
                        StandardCharsets.UTF_8
                )
        );

        for (RuntimeException failure : failures) {
            Scenario scenario = scenario(
                    List.of(room()),
                    List.of(),
                    true
            );

            when(scenario.switches().command(room(), SwitchClient.State.OFF))
                    .thenThrow(failure);

            scenario.engine().tick();

            assertEquals(
                    List.of(room()),
                    scenario.site().snapshot().pendingShutdowns()
            );

            verify(scenario.repository(), never()).save(any(), anyList());
            verifyNoInteractions(scenario.control());
        }
    }

    @Test
    void partialConfirmationFromOldRevisionCannotRemoveCurrentPendingWork() {
        Scenario scenario = scenario(
                List.of(room()),
                List.of(),
                false
        );

        long previousRevision = scenario.site().snapshot().revision();
        SiteInventory previous = scenario.site().get();

        scenario.site().replace(new SiteInventory(
                "2.1",
                previous.sitio(),
                previous.habitaciones()
        ));

        clearInvocations(scenario.repository());

        assertFalse(scenario.site().confirmShutdowns(
                previousRevision,
                List.of(room())
        ));

        assertEquals(
                List.of(room()),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository(), never()).save(any(), anyList());
    }

    @Test
    void failedPersistenceKeepsConfirmedShutdownPendingForRetry() {
        SiteInventory.Room first = room();
        SiteInventory.Room second = otherRoom();

        Scenario scenario = scenario(
                List.of(first, second),
                List.of(),
                false
        );

        when(scenario.switches().command(second, SwitchClient.State.OFF))
                .thenThrow(new ResourceAccessException("Sin conexión"));

        doThrow(new IllegalStateException("Mongo inaccesible"))
                .when(scenario.repository())
                .save(any(), anyList());

        scenario.engine().tick();

        assertEquals(
                List.of(first, second),
                scenario.site().snapshot().pendingShutdowns()
        );

        verify(scenario.repository()).save(
                scenario.site().get(),
                List.of(second)
        );

        verifyNoInteractions(scenario.control());
    }

    private Scenario scenario(
            List<SiteInventory.Room> pending,
            List<SiteInventory.Room> configured,
            boolean allowRemovedSwitches) {

        SiteInventory active = new SiteInventory(
                "2.0",
                inventory().sitio(),
                configured
        );

        SiteRepository repository = mock(SiteRepository.class);

        when(repository.load()).thenReturn(Optional.of(active));
        when(repository.loadPendingShutdowns()).thenReturn(pending);

        SiteService site = new SiteService(repository);
        ControlService control = mock(ControlService.class);
        TemperatureSubscriber subscriber =
                mock(TemperatureSubscriber.class);
        SwitchClient switches = mock(SwitchClient.class);

        when(control.status()).thenReturn(ControlStatus.stopped());
        when(control.zone()).thenReturn(ZoneId.of("America/Montevideo"));

        when(switches.command(any(), eq(SwitchClient.State.OFF)))
                .thenReturn(SwitchClient.State.OFF);

        HeatingEngine engine = new HeatingEngine(
                site,
                control,
                subscriber,
                switches
        );

        ReflectionTestUtils.setField(
                engine,
                "allowRemovedSwitches",
                allowRemovedSwitches
        );

        return new Scenario(
                repository,
                site,
                control,
                switches,
                engine
        );
    }

    private SiteInventory.Room otherRoom() {
        return new SiteInventory.Room(
                "room2",
                "Dormitorio",
                20000L,
                800L,
                "ht-room2",
                "ht-room2/status/temperature:0",
                "sw-room2",
                "http://localhost:9090"
        );
    }

    private HttpClientErrorException notFound(String message) {
        String body = """
                {"status":404,"mensaje":"%s"}
                """.formatted(message);

        return HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                new HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
    }

    private record Scenario(
            SiteRepository repository,
            SiteService site,
            ControlService control,
            SwitchClient switches,
            HeatingEngine engine) {
    }
}