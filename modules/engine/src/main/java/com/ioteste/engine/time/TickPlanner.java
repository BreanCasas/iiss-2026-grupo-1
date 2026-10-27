package com.ioteste.engine.time;

import com.ioteste.core.PeakSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Decide cuánto esperar (en tiempo REAL) hasta el próximo ciclo de control.
 *
 * Un ciclo periódico con espera fija no alcanza cuando el tiempo es
 * acelerado: con factor 60 una espera real de 1 s equivale a 1 minuto
 * virtual, y el corte de las 17:00 llegaría tarde. Por eso el ciclo se
 * adelanta para despertar justo cuando el reloj virtual llega al próximo
 * cambio de franja.
 *
 * Si la evaluación anterior (evaluatedAt) quedó vieja porque el reloj cruzó
 * un límite mientras el ciclo estaba ocupado, pide repetir enseguida.
 *
 * No depende de Spring ni del reloj del sistema: se prueba con valores.
 */
public final class TickPlanner {

    // Margen para que, al despertar, el reloj virtual ya haya
    // alcanzado el límite y no quede un milisegundo antes.
    private static final long MARGIN_NANOS = 1_000_000L;

    private TickPlanner() {
    }

    public static Duration nextDelay(
            boolean running,
            Instant virtualNow,
            Instant evaluatedAt,
            double factor,
            ZoneId zone,
            PeakSchedule schedule,
            Duration regularPeriod,
            Duration minimumDelay) {

        if (!running
                || virtualNow == null
                || !Double.isFinite(factor)
                || factor <= 0) {

            return regularPeriod;
        }

        // Si durante el último ciclo el reloj virtual cruzó un límite de
        // franja, esa evaluación quedó vieja: repetir sin esperar.
        if (evaluatedAt != null && evaluatedAt.isBefore(virtualNow)) {
            Instant crossed = schedule.nextChange(
                            LocalDateTime.ofInstant(evaluatedAt, zone))
                    .atZone(zone)
                    .toInstant();

            if (!crossed.isAfter(virtualNow)) {
                return minimumDelay;
            }
        }

        LocalDateTime localNow = LocalDateTime.ofInstant(virtualNow, zone);

        Instant boundary = schedule.nextChange(localNow)
                .atZone(zone)
                .toInstant();

        long virtualNanos = Duration.between(virtualNow, boundary).toNanos();

        long realNanos = (long) Math.ceil(virtualNanos / factor)
                + MARGIN_NANOS;

        Duration untilBoundary = Duration.ofNanos(
                Math.max(realNanos, minimumDelay.toNanos())
        );

        return untilBoundary.compareTo(regularPeriod) < 0
                ? untilBoundary
                : regularPeriod;
    }
}
