package com.ioteste.engine.numeric;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Reads a decimal JSON number and stores it as thousandths in a long. */
public final class MilliLongDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        try {
            return parser.getDecimalValue().movePointRight(3).longValueExact();
        } catch (ArithmeticException exception) {
            return (Long) context.handleWeirdNumberValue(
                    Long.class, parser.getDecimalValue(),
                    "El número debe tener como máximo tres decimales y caber en long escalado");
        }
    }
}
