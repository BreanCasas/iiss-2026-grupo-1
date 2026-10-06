package com.ioteste.subscriber.model;

import java.math.BigDecimal;

/**
 * Representa una habitación (room) del sitio controlado por EcoWarm.
 * Cada habitación tiene un termostato (sensor Shelly H&T) y un switch
 * (Shelly Pro 1PM) asociados, y una temperatura objetivo configurable.
 *
 * @param id            Identificador único de la habitación (ej. "room1")
 * @param name          Nombre descriptivo (ej. "Living")
 * @param thermostatId  Identificador del termostato/sensor asociado
 * @param switchId      Identificador del switch asociado
 * @param targetTempC   Temperatura objetivo en grados Celsius (1 decimal)
 */
public record Room(
        String id,
        String name,
        String thermostatId,
        String switchId,
        BigDecimal targetTempC
) {
}