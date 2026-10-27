package com.ioteste.engine.control;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ioteste.engine.numeric.MilliLongDeserializer;
import com.ioteste.engine.numeric.MilliLongSerializer;

import java.time.OffsetDateTime;

public record ControlCommand(
        String accion,
        @JsonFormat(
                without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        )
        OffsetDateTime fechaHora,
        @JsonDeserialize(using = MilliLongDeserializer.class)
        @JsonSerialize(using = MilliLongSerializer.class)
        Long factorTiempo
) {
}