package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import jakarta.annotation.PreDestroy;

import java.util.HashSet;
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

    private final Map<String, Double> temperatures =
            new ConcurrentHashMap<>();

    private final Set<String> subscribedTopics =
            ConcurrentHashMap.newKeySet();

    private volatile Set<String> activeTopics = Set.of();
    private MqttClient client;

    public TemperatureSubscriber(
            EnvironmentRegistration registration,
            SiteService siteService,
            ObjectMapper mapper) {

        this.registration = registration;
        this.siteService = siteService;
        this.mapper = mapper;
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
                            boolean reconnect, String serverURI) {

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
                            String topic, MqttMessage message) {

                        receive(topic, message);
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
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
            log.warn("No se pudo actualizar MQTT ({}). Reintentando.",
                    exception.getClass().getSimpleName());
        }
    }

    private void receive(String topic, MqttMessage message) {
        if (!activeTopics.contains(topic)) {
            return;
        }

        try {
            JsonNode payload = mapper.readTree(message.getPayload());
            JsonNode temperature = payload == null ? null : payload.get("tC");

            if (temperature == null
                    || !temperature.isNumber()
                    || !Double.isFinite(temperature.asDouble())) {

                log.warn("Temperatura MQTT inválida en {}", topic);
                return;
            }

            double value = temperature.asDouble();
            temperatures.put(topic, value);

            log.info("Temperatura recibida: {} = {} °C", topic, value);

        } catch (Exception exception) {
            log.warn("Mensaje MQTT inválido en {}", topic);
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