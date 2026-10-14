package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TemperatureSubscriber {

    private static final Logger log =
            LoggerFactory.getLogger(TemperatureSubscriber.class);

    private final EnvironmentRegistration registration;
    private final SiteService siteService;
    private final ObjectMapper mapper;
    private final TemperatureReadingRepository readings;

    private final Map<String, Double> temperatures =
            new ConcurrentHashMap<>();

    private final Set<String> subscribedTopics =
            ConcurrentHashMap.newKeySet();

    private volatile Set<String> activeTopics = Set.of();
    private MqttClient client;

    public TemperatureSubscriber(
            EnvironmentRegistration registration,
            SiteService siteService,
            ObjectMapper mapper,
            TemperatureReadingRepository readings) {

        this.registration = registration;
        this.siteService = siteService;
        this.mapper = mapper;
        this.readings = readings;
    }

    @Scheduled(initialDelay = 4000, fixedDelay = 2000)
    public void synchronizeSubscriptions() {
        EnvironmentRegistration.Broker broker = registration.broker();

        if (broker == null) {
            return;
        }

        try {
            SiteInventory inventory = siteService.get();
            Set<String> desiredTopics = new HashSet<>();

            for (SiteInventory.Room room : inventory.habitaciones()) {
                desiredTopics.add(room.topicTermostato());
            }

            activeTopics = Set.copyOf(desiredTopics);
            temperatures.keySet().retainAll(desiredTopics);

            if (client == null) {
                client = new MqttClient(
                        "tcp://" + broker.host() + ":" + broker.puerto(),
                        MqttClient.generateClientId(),
                        new MemoryPersistence()
                );

                client.setCallback(new MqttCallbackExtended() {

                    @Override
                    public void connectComplete(
                            boolean reconnect,
                            String serverURI) {

                        subscribedTopics.clear();
                        log.info("MQTT conectado: {}", serverURI);
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        subscribedTopics.clear();
                        log.warn("Conexión MQTT perdida");
                    }

                    @Override
                    public void messageArrived(
                            String topic,
                            MqttMessage message) {

                        receive(topic, message);
                    }

                    @Override
                    public void deliveryComplete(
                            IMqttDeliveryToken token) {
                    }
                });
            }

            if (!client.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(5);

                client.connect(options);
            }

            for (String topic : Set.copyOf(subscribedTopics)) {
                if (!desiredTopics.contains(topic)) {
                    client.unsubscribe(topic);
                    subscribedTopics.remove(topic);
                }
            }

            for (String topic : desiredTopics) {
                if (!subscribedTopics.contains(topic)) {
                    client.subscribe(topic, 0);
                    subscribedTopics.add(topic);
                    log.info("Suscripción MQTT: {}", topic);
                }
            }

        } catch (Exception exception) {
            log.warn(
                    "No se pudo actualizar MQTT ({}). Reintentando.",
                    exception.getClass().getSimpleName()
            );
        }
    }

    void receive(String topic, MqttMessage message) {
        if (!activeTopics.contains(topic)) {
            return;
        }

        JsonNode payload;
        List<SiteInventory.Room> rooms;
        Instant receivedAt = Instant.now();

        try {
            payload = mapper.reader()
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(message.getPayload());

            if (payload instanceof ObjectNode object) {
                normalizeTimestamp(object);
            }

            JsonNode temperature =
                    payload == null ? null : payload.get("tC");

            if (temperature == null
                    || !temperature.isNumber()
                    || !Double.isFinite(temperature.asDouble())) {

                log.warn("Temperatura MQTT inválida en {}", topic);
                return;
            }

            rooms = siteService.get().habitaciones().stream()
                    .filter(room -> topic.equals(room.topicTermostato()))
                    .toList();

            if (rooms.isEmpty()) {
                return;
            }

        } catch (Exception exception) {
            log.warn(
                    "No se pudo procesar la lectura MQTT en {} ({}).",
                    topic,
                    exception.getClass().getSimpleName()
            );
            return;
        }

        double value = payload.get("tC").asDouble();

        temperatures.put(topic, value);

        log.info("Temperatura recibida: {} = {} °C", topic, value);

        for (SiteInventory.Room room : rooms) {
            try {
                readings.save(room, topic, payload, receivedAt);

            } catch (Exception exception) {
                log.error(
                        "No se pudo guardar la lectura de {} en MongoDB ({}). "
                                + "La temperatura sigue disponible en memoria.",
                        room.id(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void normalizeTimestamp(ObjectNode payload) {
        JsonNode timestamp = payload.get("ts");

        if (timestamp == null || timestamp.isNull()) {
            return;
        }

        if (!timestamp.isNumber()) {
            throw new IllegalArgumentException(
                    "ts debe ser un número de segundos"
            );
        }

        if (timestamp.isDouble() || timestamp.isFloat()) {
            throw new IllegalArgumentException(
                    "ts debe leerse exactamente, sin coma flotante binaria"
            );
        }

        try {
            long epochMillis = timestamp.decimalValue()
                    .movePointRight(3)
                    .longValueExact();

            payload.put("ts", epochMillis);

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "ts debe caber en long y tener precisión de milisegundos",
                    exception
            );
        }
    }

    public Double temperature(String topic) {
        return temperatures.get(topic);
    }

    @PreDestroy
    public void close() {
        if (client == null) {
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }

            client.close();

        } catch (MqttException exception) {
            log.warn("No se pudo cerrar limpiamente MQTT");
        }
    }
}