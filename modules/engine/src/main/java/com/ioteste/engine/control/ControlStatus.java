package com.ioteste.engine.control;

import java.time.Instant;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ioteste.engine.numeric.MilliLongSerializer;

public record ControlStatus(
        State estado,
        Instant fechaHora,
        @JsonSerialize(using = MilliLongSerializer.class)
        Long factorTiempo,
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