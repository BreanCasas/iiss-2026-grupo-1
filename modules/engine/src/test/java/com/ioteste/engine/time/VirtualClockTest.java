package com.ioteste.engine.time;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualClockTest {

    @Test
    void advancesOneVirtualMinutePerRealSecondWithFactor60() {
        AtomicLong realNanos = new AtomicLong(0);

        Instant initialTime =
                Instant.parse("2026-10-05T16:59:00Z");

        VirtualClock clock = new VirtualClock(
                initialTime,
                60.0,
                realNanos::get
        );

        realNanos.set(1_000_000_000L);

        assertEquals(
                Instant.parse("2026-10-05T17:00:00Z"),
                clock.now()
        );
    }

    @Test
    void returnsInitialTimeBeforeAnyTimeHasElapsed() {
        AtomicLong realNanos = new AtomicLong(5_000_000_000L);
        Instant initialTime =
                Instant.parse("2026-10-05T16:59:00Z");

        VirtualClock clock = new VirtualClock(
                initialTime, 60.0, realNanos::get
        );

        assertEquals(initialTime, clock.now());
    }

    @Test
    void advancesAtNormalSpeedWithFactorOne() {
        AtomicLong realNanos = new AtomicLong(5_000_000_000L);
        Instant initialTime =
                Instant.parse("2026-10-05T16:59:00Z");

        VirtualClock clock = new VirtualClock(
                initialTime, 1.0, realNanos::get
        );

        realNanos.set(7_000_000_000L);

        assertEquals(initialTime.plusSeconds(2), clock.now());
    }
}