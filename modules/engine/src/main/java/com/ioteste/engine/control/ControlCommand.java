package com.ioteste.engine.control;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record ControlCommand(
        String accion,
        @JsonFormat(
                without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        )
        OffsetDateTime fechaHora,
        Double factorTiempo
) {
}