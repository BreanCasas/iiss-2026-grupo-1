package com.ioteste.engine.time;

import com.ioteste.core.HeatingController;
import com.ioteste.core.PeakSchedule;
import com.ioteste.core.RoomState;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualHeatingTest {

    @Test
    void stopsAtPeakStartAndResumesAtPeakEndUsingVirtualTime() {
        AtomicLong realNanos = new AtomicLong(0);

        VirtualClock clock = new VirtualClock(
                Instant.parse("2026-10-05T16:59:00Z"),
                60.0,
                realNanos::get
        );

        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        HeatingController controller = new HeatingController();

        List<RoomState> rooms = List.of(
                new RoomState("room1", 19.0, 21.5, 1.2)
        );

        assertEquals(Set.of("room1"), controller.selectRoomsToHeat(
                rooms, 3.7,
                schedule.isPeak(LocalDateTime.ofInstant(
                        clock.now(), ZoneOffset.UTC))
        ));

        // Un segundo real simulado: llegamos a las 17:00 virtuales.
        realNanos.set(1_000_000_000L);

        assertEquals(Set.of(), controller.selectRoomsToHeat(
                rooms, 3.7,
                schedule.isPeak(LocalDateTime.ofInstant(
                        clock.now(), ZoneOffset.UTC))
        ));

        // 361 segundos desde el inicio: llegamos a las 23:00.
        realNanos.set(361_000_000_000L);

        assertEquals(Set.of("room1"), controller.selectRoomsToHeat(
                rooms, 3.7,
                schedule.isPeak(LocalDateTime.ofInstant(
                        clock.now(), ZoneOffset.UTC))
        ));
    }
}