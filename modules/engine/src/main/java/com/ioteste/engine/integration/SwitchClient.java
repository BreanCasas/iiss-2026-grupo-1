package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ioteste.engine.site.SiteInventory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SwitchClient {

    public enum State {
        ON,
        OFF,
        DESCONOCIDO
    }

    private final RestClient client;

    public SwitchClient(
            @Value("${ECOWARM_ENVIRONMENT_KEY:}") String environmentKey) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(3));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory);

        if (!environmentKey.isBlank()) {
            builder.defaultHeader("X-API-Key", environmentKey);
        }

        client = builder.build();
    }

    public State status(SiteInventory.Room room) {
        JsonNode response = client.get()
                .uri(endpoint(room))
                .retrieve()
                .body(JsonNode.class);

        return state(response);
    }

    public State command(SiteInventory.Room room, State desired) {
        if (desired == State.DESCONOCIDO) {
            throw new IllegalArgumentException(
                    "Solo se puede comandar ON u OFF");
        }

        JsonNode response = client.post()
                .uri(endpoint(room) + "/comandos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accion", desired.name()))
                .retrieve()
                .body(JsonNode.class);

        State applied = state(response);

        if (applied != desired) {
            throw new IllegalStateException(
                    "El switch no confirmó el estado solicitado");
        }

        return applied;
    }

    private State state(JsonNode response) {
        if (response == null || !response.path("estado").isTextual()) {
            throw new IllegalStateException(
                    "Respuesta del switch sin estado");
        }

        return State.valueOf(response.path("estado").asText());
    }

    private String endpoint(SiteInventory.Room room) {
        return room.urlSwitch().replaceAll("/+$", "")
                + "/switches/"
                + org.springframework.web.util.UriUtils.encodePathSegment(
                room.idSwitch(),
                java.nio.charset.StandardCharsets.UTF_8
        );
    }
}