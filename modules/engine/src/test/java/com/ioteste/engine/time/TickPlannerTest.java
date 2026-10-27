package com.ioteste.engine.time;

import com.ioteste.core.PeakSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickPlannerTest {

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");
    private static final Duration PERIOD = Duration.ofSeconds(1);
    private static final Duration MINIMUM = Duration.ofMillis(20);

    private final PeakSchedule schedule = new PeakSchedule(
            LocalTime.of(17, 0),
            LocalTime.of(23, 0),
            PeakSchedule.Days.HABILES
    );

    private Instant at(String localTime) {
        return OffsetDateTime.parse(localTime).toInstant();
    }

    private Duration delay(
            boolean running,
            Instant now,
            Instant evaluatedAt,
            double factor) {

        return TickPlanner.nextDelay(
                running, now, evaluatedAt, factor,
                URUGUAY, schedule, PERIOD, MINIMUM
        );
    }

    @Test
    void usesTheRegularPeriodWhenControlIsStopped() {
        assertEquals(PERIOD, delay(false,
                at("2026-10-05T16:59:30-03:00"), null, 60));
    }

    @Test
    void usesTheRegularPeriodWithoutAVirtualTime() {
        assertEquals(PERIOD, delay(true, null, null, 60));
    }

    @Test
    void usesTheRegularPeriodWhenTheFactorIsNotValid() {
        Instant now = at("2026-10-05T16:59:30-03:00");

        assertEquals(PERIOD, delay(true, now, null, 0));
        assertEquals(PERIOD, delay(true, now, null, -2));
        assertEquals(PERIOD, delay(true, now, null, Double.NaN));
        assertEquals(PERIOD, delay(true, now, null,
                Double.POSITIVE_INFINITY));
    }

    @Test
    void usesTheRegularPeriodWhenTheNextChangeIsFarAway() {
        assertEquals(PERIOD, delay(true,
                at("2026-10-05T10:00:00-03:00"), null, 60));
    }

    @Test
    void wakesAtThePeakStartWhenItIsCloserThanThePeriod() {
        // Faltan 30 s virtuales; a factor 60 son 0,5 s reales.
        Duration result = delay(true,
                at("2026-10-05T16:59:30-03:00"), null, 60);

        assertTrue(result.toMillis() >= 500 && result.toMillis() <= 502,
                "Debe despertar a los ~500 ms: " + result);
    }

    @Test
    void wakesAtThePeakEnd() {
        // 22:59:59 a factor 1000: falta 1 s virtual = 1 ms real.
        Duration result = delay(true,
                at("2026-10-05T22:59:59-03:00"), null, 1000);

        assertEquals(MINIMUM, result);
    }

    @Test
    void neverWaitsLessThanTheMinimumDelay() {
        Instant now = at("2026-10-05T17:00:00-03:00").minusMillis(10);

        assertEquals(MINIMUM, delay(true, now, null, 1));
    }

    @Test
    void waitsForTheNextPeakAfterFridayEvening() {
        // El próximo cambio es el lunes: lejos, período normal.
        assertEquals(PERIOD, delay(true,
                at("2026-10-23T23:30:00-03:00"), null, 60));
    }

    @Test
    void repeatsAtOnceWhenALimitWasCrossedDuringTheLastCycle() {
        Instant evaluatedAt = at("2026-10-05T16:59:59.800-03:00");
        Instant now = at("2026-10-05T17:00:00.200-03:00");

        assertEquals(MINIMUM, delay(true, now, evaluatedAt, 1));
    }

    @Test
    void doesNotRepeatWhenNoLimitWasCrossedSinceTheLastEvaluation() {
        Instant evaluatedAt = at("2026-10-05T17:00:00.200-03:00");
        Instant now = at("2026-10-05T17:00:01.200-03:00");

        assertEquals(PERIOD, delay(true, now, evaluatedAt, 1));
    }

    @Test
    void cutOffAtPeakStartArrivesWithinOneRealSecondAtFactor600() {
        // Simula el ciclo con un reloj controlado, sin esperar tiempo real:
        // cada vuelta evalúa y avanza el reloj lo que indica el planificador.
        double factor = 600;
        Instant boundary = at("2026-10-05T17:00:00-03:00");
        Instant virtual = boundary.minusSeconds(45);
        Instant evaluatedAt = null;

        Instant firstEvaluationInPeak = null;

        for (int cycle = 0; cycle < 10_000 && firstEvaluationInPeak == null;
                cycle++) {

            evaluatedAt = virtual;

            if (!virtual.isBefore(boundary)) {
                firstEvaluationInPeak = virtual;
                break;
            }

            Duration wait = delay(true, virtual, evaluatedAt, factor);

            virtual = virtual.plusNanos(
                    (long) (wait.toNanos() * factor));
        }

        long lateVirtualMillis = Duration.between(
                boundary, firstEvaluationInPeak).toMillis();

        // Antes: hasta ~618 s de retraso. Ahora: menos de 1 s real
        // (600 s virtuales) y, en la práctica, unos pocos segundos.
        assertTrue(lateVirtualMillis >= 0 && lateVirtualMillis < 30_000,
                "Retraso virtual excesivo: " + lateVirtualMillis + " ms");
    }
}
