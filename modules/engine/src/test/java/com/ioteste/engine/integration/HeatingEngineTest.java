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
                21.5,
                1.2,
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
                        3.7,
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