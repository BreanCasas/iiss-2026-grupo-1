package com.ioteste.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record PeakSchedule(
        LocalTime from,
        LocalTime until,
        Days days
) {

    public enum Days {
        HABILES,
        TODOS
    }

    public boolean isPeak(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();

        return appliesOn(dateTime.toLocalDate())
                && !time.isBefore(from)
                && time.isBefore(until);
    }

    /**
     * Próximo instante local, estrictamente posterior a {@code after},
     * en que isPeak cambia de valor: inicio o fin de una franja.
     *
     * Sirve para despertar al controlador justo en el cambio de franja
     * y no depender de la frecuencia con que se evalúa.
     */
    public LocalDateTime nextChange(LocalDateTime after) {
        LocalDate firstDay = after.toLocalDate();

        // Hay una franja en cualquier ventana de 7 días consecutivos.
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate day = firstDay.plusDays(offset);

            if (!appliesOn(day)) {
                continue;
            }

            LocalDateTime start = day.atTime(from);

            if (start.isAfter(after)) {
                return start;
            }

            LocalDateTime end = day.atTime(until);

            if (end.isAfter(after)) {
                return end;
            }
        }

        throw new IllegalStateException(
                "La franja no se aplica ningún día: " + this);
    }

    private boolean appliesOn(LocalDate date) {
        return days == Days.TODOS
                || date.getDayOfWeek().getValue() <= 5;
    }
}
