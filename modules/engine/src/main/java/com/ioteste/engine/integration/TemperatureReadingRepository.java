package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ioteste.engine.site.SiteInventory;

import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TemperatureReadingRepository {

    private final MongoTemplate mongo;

    public TemperatureReadingRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void save(
            SiteInventory.Room room,
            String topic,
            JsonNode payload,
            Instant receivedAt) {

        Long sourceTs = optionalTimestamp(payload);

        Document storedPayload =
                Document.parse(payload.toString());

        if (sourceTs != null) {
            // Mantener BSON Int64 también dentro del payload.
            storedPayload.put("ts", sourceTs);
        }

        Document reading = new Document()
                .append("roomId", room.id())
                .append("roomName", room.nombre())
                .append("thermostatId", room.idTermostato())
                .append("topic", topic)
                .append("tempCelsius", payload.get("tC").asDouble())
                .append("tempFahrenheit", optionalNumber(payload, "tF"))
                .append("sourceTs", sourceTs)
                .append("sourceTsUnit", "epoch_milliseconds")
                .append("receivedAt", Date.from(receivedAt))
                .append("payload", storedPayload);

        mongo.getCollection("temperature_readings")
                .insertOne(reading);
    }

    private Long optionalTimestamp(JsonNode payload) {
        JsonNode value = payload.get("ts");

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(
                    "ts interno debe ser long en milisegundos"
            );
        }

        return value.longValue();
    }

    private Double optionalNumber(JsonNode payload, String field) {
        JsonNode value = payload.get(field);

        if (value == null || !value.isNumber()) {
            return null;
        }

        double number = value.asDouble();

        return Double.isFinite(number) ? number : null;
    }
}