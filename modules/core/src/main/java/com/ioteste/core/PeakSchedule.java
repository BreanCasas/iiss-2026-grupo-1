package com.ioteste.core;

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

        boolean applicableDay = days == Days.TODOS
                || dateTime.getDayOfWeek().getValue() <= 5;

        return applicableDay
                && !time.isBefore(from)
                && time.isBefore(until);
    }
}