package com.ioteste.subscriber.model;

import java.time.Instant;

/**
 * Representa una lectura de temperatura registrada para una habitación,
 * con marca de fecha/hora de cuándo fue recibida por el sistema.
 *
 * @param roomId         Identificador de la habitación asociada
 * @param tempCelsius    Temperatura en grados Celsius
 * @param tempFahrenheit Temperatura en grados Fahrenheit
 * @param sourceTs       Timestamp original del mensaje (epoch segundos, con decimales de ms)
 * @param receivedAt     Momento en que el sistema recibió y persistió la lectura
 */
public record TemperatureReading(
        String roomId,
        double tempCelsius,
        double tempFahrenheit,
        long sourceTs,
        Instant receivedAt
) {
}