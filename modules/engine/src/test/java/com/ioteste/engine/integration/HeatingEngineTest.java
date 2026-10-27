package com.ioteste.engine.integration;

import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import java.util.List;

import org.junit.jupiter.api.Test;

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
}