package com.ioteste.engine.control;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ioteste.engine.numeric.MilliLongSerializer;

public record ControlStatus(
        State estado,
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "UTC")
        Instant fechaHora,
        @JsonSerialize(using = MilliLongSerializer.class)
        Long factorTiempo,
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                timezone = "UTC")
        Instant iniciadoEn
) {

    public enum State {
        DETENIDO,
        EN_EJECUCION
    }

    public static ControlStatus stopped() {
        return new ControlStatus(
                State.DETENIDO,
                null,
                null,
                null
        );
    }
}