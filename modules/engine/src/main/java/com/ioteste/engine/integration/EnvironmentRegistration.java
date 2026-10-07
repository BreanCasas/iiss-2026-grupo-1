package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@EnableScheduling
public class EnvironmentRegistration {

    private static final Logger log =
            LoggerFactory.getLogger(EnvironmentRegistration.class);

    public record Broker(String host, int puerto) {
    }

    private final RestClient client;
    private final String environmentUrl;
    private final String label;
    private final String publicUrl;
    private final String apiKey;
    private final String environmentKey;

    private volatile Broker broker;

    public EnvironmentRegistration(
            @Value("${ECOWARM_ENVIRONMENT_URL:}") String environmentUrl,
            @Value("${ECOWARM_LABEL:grupo-1}") String label,
            @Value("${ECOWARM_PUBLIC_URL:http://localhost:8080}") String publicUrl,
            @Value("${IOTESTE_API_KEY}") String apiKey,
            @Value("${ECOWARM_ENVIRONMENT_KEY:}") String environmentKey) {

        this.environmentUrl = environmentUrl;
        this.label = label;
        this.publicUrl = publicUrl;
        this.apiKey = apiKey;
        this.environmentKey = environmentKey;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(5));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Scheduled(initialDelay = 3000, fixedDelay = 5000)
    public void register() {
        if (environmentUrl.isBlank() || broker != null) {
            return;
        }

        try {
            RestClient.RequestBodySpec request = client.post()
                    .uri(environmentUrl + "/controladores")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON);

            if (!environmentKey.isBlank()) {
                request.header("X-API-Key", environmentKey);
            }

            JsonNode response = request
                    .body(Map.of(
                            "etiqueta", label,
                            "baseUrl", publicUrl,
                            "apiKey", apiKey
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode mqtt = response == null ? null : response.get("mqtt");

            if (mqtt == null
                    || !mqtt.path("host").isTextual()
                    || mqtt.path("host").asText().isBlank()
                    || !mqtt.path("puerto").isIntegralNumber()
                    || mqtt.path("puerto").asInt() < 1
                    || mqtt.path("puerto").asInt() > 65535) {

                throw new IllegalStateException(
                        "El registro no devolvió un broker válido");
            }

            broker = new Broker(
                    mqtt.path("host").asText(),
                    mqtt.path("puerto").asInt()
            );

            log.info("Registro completado. Broker MQTT: {}:{}",
                    broker.host(), broker.puerto());

        } catch (Exception exception) {
            log.warn("No se pudo registrar el controlador ({}). Reintentando.",
                    exception.getClass().getSimpleName());
        }
    }

    public Broker broker() {
        return broker;
    }
}