package com.ioteste.subscriber.repository;

import com.ioteste.subscriber.model.TemperatureReading;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;

/**
 * Repositorio de lecturas de temperatura persistidas en MongoDB.
 * Cada lectura se almacena como un documento en la colección
 * temperature_readings.
 */
public class TemperatureReadingRepository {

    private static final Logger log =
            LoggerFactory.getLogger(TemperatureReadingRepository.class);

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    public TemperatureReadingRepository(String mongoUri, String databaseName) {
        this.mongoClient = MongoClients.create(mongoUri);

        MongoDatabase database = mongoClient.getDatabase(databaseName);

        this.collection = database.getCollection("temperature_readings");
    }

    /**
     * Persiste una nueva lectura de temperatura en MongoDB.
     */
    public boolean append(TemperatureReading reading) {
        try {
            Document document = new Document()
                    .append("roomId", reading.roomId())
                    .append("tempCelsius", reading.tempCelsius())
                    .append("tempFahrenheit", reading.tempFahrenheit())
                    .append("sourceTs", reading.sourceTs())
                    .append("receivedAt", Date.from(reading.receivedAt()));

            collection.insertOne(document);

            log.debug(
                    "Lectura persistida en MongoDB: room={} tC={}",
                    reading.roomId(),
                    reading.tempCelsius()
            );

            return true;

        } catch (Exception e) {
            log.error(
                    "Error persistiendo lectura en MongoDB: {}",
                    e.getMessage(),
                    e
            );

            return false;
        }
    }

    public void close() {
        mongoClient.close();
    }
}