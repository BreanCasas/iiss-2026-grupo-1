package com.ioteste.core;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PeakScheduleTest {

    @Test
    void recognizesPeakHoursOnMonday() {
        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        boolean result = schedule.isPeak(
                LocalDateTime.of(2026, 10, 5, 18, 0)
        );

        assertTrue(result,
                "Un lunes a las 18:00 debe estar dentro de la punta");
    }

    @Test
    void excludesSaturdayWhenScheduleIsWeekdaysOnly() {
        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        boolean result = schedule.isPeak(
                LocalDateTime.of(2026, 10, 10, 18, 0)
        );

        assertFalse(result,
                "El sábado no debe ser punta cuando los días son HABILES");
    }

    @Test
    void includesExactStartTime() {
        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        assertTrue(schedule.isPeak(
                LocalDateTime.of(2026, 10, 5, 17, 0)
        ));
    }

    @Test
    void excludesExactEndTime() {
        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        assertFalse(schedule.isPeak(
                LocalDateTime.of(2026, 10, 5, 23, 0)
        ));
    }

    @Test
    void includesSundayWhenScheduleAppliesEveryDay() {
        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.TODOS
        );

        assertTrue(schedule.isPeak(
                LocalDateTime.of(2026, 10, 11, 18, 0)
        ));
    }

    private PeakSchedule weekdays() {
        return new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );
    }

    @Test
    void fridayLateEveningIsStillPeak() {
        // Viernes 22:30 local.
        assertTrue(weekdays().isPeak(
                LocalDateTime.of(2026, 10, 23, 22, 30)));
    }

    @Test
    void sundayLateEveningIsNotPeak() {
        // Domingo 22:30 local.
        assertFalse(weekdays().isPeak(
                LocalDateTime.of(2026, 10, 25, 22, 30)));
    }

    @Test
    void nextChangeBeforeStartIsTheStartOfTheSameDay() {
        assertEquals(
                LocalDateTime.of(2026, 10, 5, 17, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 5, 16, 59, 59))
        );
    }

    @Test
    void nextChangeInsidePeakIsTheEnd() {
        assertEquals(
                LocalDateTime.of(2026, 10, 5, 23, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 5, 18, 0))
        );
    }

    @Test
    void nextChangeAtExactStartIsTheEndNotTheStartAgain() {
        assertEquals(
                LocalDateTime.of(2026, 10, 5, 23, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 5, 17, 0))
        );
    }

    @Test
    void nextChangeAtExactEndIsTheNextDayStart() {
        assertEquals(
                LocalDateTime.of(2026, 10, 6, 17, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 5, 23, 0))
        );
    }

    @Test
    void nextChangeAfterFridayPeakSkipsTheWeekend() {
        // Viernes 23:00 -> lunes 17:00.
        assertEquals(
                LocalDateTime.of(2026, 10, 26, 17, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 23, 23, 0))
        );
    }

    @Test
    void nextChangeOnSaturdayIsMondayStart() {
        assertEquals(
                LocalDateTime.of(2026, 10, 12, 17, 0),
                weekdays().nextChange(
                        LocalDateTime.of(2026, 10, 10, 12, 0))
        );
    }

    @Test
    void nextChangeWithEveryDayScheduleIncludesTheWeekend() {
        PeakSchedule everyDay = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.TODOS
        );

        assertEquals(
                LocalDateTime.of(2026, 10, 11, 17, 0),
                everyDay.nextChange(
                        LocalDateTime.of(2026, 10, 10, 23, 0))
        );
    }
}
