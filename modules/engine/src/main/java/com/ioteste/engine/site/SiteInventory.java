package com.ioteste.engine.site;

import com.ioteste.core.PeakSchedule;

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
            Double potenciaContratadaKW,
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
            Double temperaturaEsperada,
            Double potenciaKW,
            String idTermostato,
            String topicTermostato,
            String idSwitch,
            String urlSwitch
    ) {
    }
}