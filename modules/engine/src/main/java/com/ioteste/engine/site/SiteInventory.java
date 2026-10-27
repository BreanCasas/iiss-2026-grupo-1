package com.ioteste.engine.site;

import com.ioteste.core.PeakSchedule;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.ioteste.engine.numeric.MilliLongDeserializer;
import com.ioteste.engine.numeric.MilliLongSerializer;

import java.util.List;

public record SiteInventory(
        String schemaVersion,
        Site sitio,
        List<Room> habitaciones
) {

    public SiteInventory {
        if (habitaciones != null) {
            habitaciones = List.copyOf(habitaciones);
        }
    }

    public record Site(
            String id,
            String nombre,
            @JsonDeserialize(using = MilliLongDeserializer.class)
            @JsonSerialize(using = MilliLongSerializer.class)
            Long potenciaContratadaKW,
            Tariff tarifa
    ) {
    }

    public record Tariff(
            PeakPeriod punta
    ) {
    }

    public record PeakPeriod(
            String desde,
            String hasta,
            PeakSchedule.Days dias
    ) {
    }

    public record Room(
            String id,
            String nombre,
            @JsonDeserialize(using = MilliLongDeserializer.class)
            @JsonSerialize(using = MilliLongSerializer.class)
            Long temperaturaEsperada,
            @JsonDeserialize(using = MilliLongDeserializer.class)
            @JsonSerialize(using = MilliLongSerializer.class)
            Long potenciaKW,
            String idTermostato,
            String topicTermostato,
            String idSwitch,
            String urlSwitch
    ) {
    }
}