package com.ioteste.subscriber.repository;

import com.ioteste.subscriber.model.Room;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RoomRepository {

    private static final Logger log =
            LoggerFactory.getLogger(RoomRepository.class);

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    public RoomRepository(String mongoUri, String databaseName) {
        this.mongoClient = MongoClients.create(mongoUri);

        MongoDatabase database = mongoClient.getDatabase(databaseName);

        this.collection = database.getCollection("rooms");
    }

    public List<Room> findAll() {
        List<Room> rooms = new ArrayList<>();

        for (Document document : collection.find()) {
            rooms.add(toRoom(document));
        }

        log.info("Cargadas {} habitaciones desde MongoDB", rooms.size());

        return rooms;
    }

    public Room findByThermostatId(String thermostatId) {
        Document document = collection
                .find(new Document("thermostatId", thermostatId))
                .first();

        if (document == null) {
            return null;
        }

        return toRoom(document);
    }

    private Room toRoom(Document document) {
        return new Room(
                document.getString("_id"),
                document.getString("name"),
                document.getString("thermostatId"),
                document.getString("switchId"),
                toBigDecimal(document.get("targetTempC"))
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof org.bson.types.Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        return new BigDecimal(value.toString());
    }

    public void close() {
        mongoClient.close();
    }
}