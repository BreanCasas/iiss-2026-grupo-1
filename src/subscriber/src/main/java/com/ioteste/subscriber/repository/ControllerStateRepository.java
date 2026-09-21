package com.ioteste.subscriber.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

public class ControllerStateRepository {

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    public ControllerStateRepository(String mongoUri, String databaseName) {
        this.mongoClient = MongoClients.create(mongoUri);

        MongoDatabase database = mongoClient.getDatabase(databaseName);

        this.collection = database.getCollection("controller_state");
    }

    public boolean isEnabled() {
        Document state = collection.find(eq("_id", "controller")).first();

        if (state == null) {
            return false;
        }

        return state.getBoolean("enabled", false);
    }

    public void close() {
        mongoClient.close();
    }
}