package com.ioteste.engine.site;

import java.net.URI;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public class SiteValidator {

    public void validate(SiteInventory inventory) {
        require(inventory != null, "El inventario es obligatorio");

        require(
                inventory.schemaVersion() != null
                        && inventory.schemaVersion().matches("2\\.[0-9]+"),
                "schemaVersion debe tener formato 2.x"
        );

        require(inventory.sitio() != null, "sitio es obligatorio");
        require(
                inventory.habitaciones() != null,
                "habitaciones es obligatorio"
        );

        SiteInventory.Site site = inventory.sitio();

        requireText(site.id(), "sitio.id");
        requireText(site.nombre(), "sitio.nombre");
        requirePositive(site.potenciaContratadaKW(), "potenciaContratadaKW");

        require(site.tarifa() != null, "tarifa es obligatoria");
        require(site.tarifa().punta() != null, "punta es obligatoria");

        validatePeakPeriod(site.tarifa().punta());

        Set<String> roomIds = new HashSet<>();
        Set<String> thermostatIds = new HashSet<>();
        Set<String> switchIds = new HashSet<>();

        for (SiteInventory.Room room : inventory.habitaciones()) {
            require(room != null, "La habitación no puede ser null");

            requireText(room.id(), "habitación.id");
            requireText(room.nombre(), "habitación.nombre");
            require(
                    room.nombre().length() <= 50,
                    "El nombre de habitación admite hasta 50 caracteres"
            );

            require(
                    room.temperaturaEsperada() != null
                            && Double.isFinite(room.temperaturaEsperada()),
                    "temperaturaEsperada debe ser un número finito"
            );

            requirePositive(room.potenciaKW(), "potenciaKW");
            requireText(room.idTermostato(), "idTermostato");
            requireText(room.topicTermostato(), "topicTermostato");
            requireText(room.idSwitch(), "idSwitch");
            validateSwitchUrl(room.urlSwitch());

            require(
                    roomIds.add(room.id()),
                    "ID de habitación repetido: " + room.id()
            );

            require(
                    thermostatIds.add(room.idTermostato()),
                    "ID de termostato repetido: " + room.idTermostato()
            );

            require(
                    switchIds.add(room.idSwitch()),
                    "ID de switch repetido: " + room.idSwitch()
            );
        }
    }

    private void validatePeakPeriod(SiteInventory.PeakPeriod peak) {
        require(peak.dias() != null, "dias es obligatorio");

        LocalTime from = parseTime(peak.desde());
        LocalTime until = parseTime(peak.hasta());

        require(
                from.isBefore(until),
                "La franja punta requiere desde anterior a hasta"
        );
    }

    private LocalTime parseTime(String value) {
        require(
                value != null
                        && value.matches("([01][0-9]|2[0-3]):[0-5][0-9]"),
                "La hora debe tener formato HH:mm entre 00:00 y 23:59"
        );

        return LocalTime.parse(value);
    }

    private void validateSwitchUrl(String value) {
        requireText(value, "urlSwitch");

        URI uri = URI.create(value);

        require(
                ("http".equalsIgnoreCase(uri.getScheme())
                        || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null,
                "urlSwitch debe ser una URL HTTP o HTTPS válida sin credenciales"
        );
    }

    private void requireText(String value, String field) {
        require(
                value != null && !value.isBlank(),
                field + " no puede estar vacío"
        );
    }

    private void requirePositive(Double value, String field) {
        require(
                value != null && Double.isFinite(value) && value > 0,
                field + " debe ser un número mayor que cero"
        );
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}