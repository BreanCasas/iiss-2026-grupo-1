package com.ioteste.core;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
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
}