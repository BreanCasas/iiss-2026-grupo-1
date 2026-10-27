package com.ioteste.engine.numeric;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;

/** Writes a long stored in thousandths as a decimal JSON number. */
public final class MilliLongSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator generator,
                          SerializerProvider serializers) throws IOException {
        generator.writeNumber(BigDecimal.valueOf(value, 3).stripTrailingZeros());
    }
}
