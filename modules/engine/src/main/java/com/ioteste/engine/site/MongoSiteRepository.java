package com.ioteste.engine.site;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.ReplaceOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import static com.mongodb.client.model.Filters.eq;

@Repository
public class MongoSiteRepository implements SiteRepository {

    private final MongoTemplate mongo;
    private final ObjectMapper mapper;

    public MongoSiteRepository(
            MongoTemplate mongo,
            ObjectMapper mapper) {

        this.mongo = mongo;
        this.mapper = mapper;
    }

    private Document stored() {
        return mongo.getCollection("site_config")
                .find(eq("_id", "active"))
                .first();
    }

    @Override
    public Optional<SiteInventory> load() {
        Document data = stored();

        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(mapper.convertValue(
                data.get("inventory"),
                SiteInventory.class
        ));
    }

    @Override
    public List<SiteInventory.Room> loadPendingShutdowns() {
        Document data = stored();

        if (data == null || data.get("pendingShutdowns") == null) {
            return List.of();
        }

        return mapper.convertValue(
                data.get("pendingShutdowns"),
                new TypeReference<List<SiteInventory.Room>>() {}
        );
    }

    @Override
    public void save(
            SiteInventory inventory,
            List<SiteInventory.Room> pendingShutdowns) {

        try {
            Document replacement = Document.parse(
                    mapper.writeValueAsString(Map.of(
                            "inventory", inventory,
                            "pendingShutdowns", pendingShutdowns
                    ))
            );

            replacement.put("_id", "active");

            mongo.getCollection("site_config").replaceOne(
                    eq("_id", "active"),
                    replacement,
                    new ReplaceOptions().upsert(true)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar el inventario",
                    exception
            );
        }
    }
}