package com.ioteste.core;

public record RoomState(
        String id,
        double currentTemperature,
        double targetTemperature,
        double requiredPowerKw
) {
}