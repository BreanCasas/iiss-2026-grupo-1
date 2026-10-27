package com.ioteste.core;

/** Numeric values use thousandths: milli-degrees Celsius and milli-kW. */
public record RoomState(
        String id,
        long currentTemperature,
        long targetTemperature,
        long requiredPowerKw
) {
}
