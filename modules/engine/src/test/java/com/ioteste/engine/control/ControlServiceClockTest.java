package com.ioteste.engine.control;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlServiceClockTest {

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    private final AtomicLong nanos = new AtomicLong(0);
    private final Instant wallClock =
            Instant.parse("2026-10-05T19:00:00Z");

    private ControlService service() {
        return new ControlService(URUGUAY, nanos::get, () -> wallClock);
    }

    private ControlCommand start(String dateTime, Double factor) {
        return new ControlCommand(
                "INICIAR",
                OffsetDateTime.parse(dateTime),
                factor
        );
    }

    @Test
    void virtualTimeAdvancesOnlyWhenTheInjectedClockAdvances() {
        ControlService service = service();

        service.command(start("2026-10-05T16:59:00-03:00", 60.0));

        assertEquals(
                OffsetDateTime.parse("2026-10-05T16:59:00-03:00")
                        .toInstant(),
                service.status().fechaHora()
        );

        // Un segundo real a factor 60 = un minuto virtual: las 17:00.
        nanos.set(1_000_000_000L);

        assertEquals(
                OffsetDateTime.parse("2026-10-05T17:00:00-03:00")
                        .toInstant(),
                service.status().fechaHora()
        );
    }

    @Test
    void startMomentComesFromTheInjectedWallClock() {
        ControlService service = service();

        service.command(start("2026-10-05T16:59:00-03:00", 1.0));

        assertEquals(wallClock, service.status().iniciadoEn());
    }

    @Test
    void listenersAreNotifiedOnStartAndStop() {
        ControlService service = service();
        AtomicInteger notifications = new AtomicInteger();

        service.onChange(notifications::incrementAndGet);

        service.command(start("2026-10-05T16:59:00-03:00", 1.0));
        service.command(new ControlCommand("DETENER", null, null));

        assertEquals(2, notifications.get());
    }
}
