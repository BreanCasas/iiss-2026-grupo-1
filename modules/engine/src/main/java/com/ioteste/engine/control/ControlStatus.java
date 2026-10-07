package com.ioteste.engine.control;

import java.time.Instant;

public record ControlStatus(
        State estado,
        Instant fechaHora,
        Double factorTiempo,
        Instant iniciadoEn
) {

    public enum State {
        DETENIDO,
        EN_EJECUCION
    }

    public static ControlStatus stopped() {
        return new ControlStatus(
                State.DETENIDO, null, null, null
        );
    }
}