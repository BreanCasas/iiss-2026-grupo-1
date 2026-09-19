package com.ioteste.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generador de eventos de termostatos (datos de prueba).
 *
 * Cliente Java que simula el envío periódico de eventos de temperatura
 * de sensores Shelly H&T, publicando por MQTT con el formato:
 *
 *   Topic:   ht-sim-room1/status/temperature:0
 *   Payload: {"id":0,"tC":21.4,"tF":70.5,"ts":1786840680.123}
 *
 * Configuración vía variables de entorno:
 *   MQTT_BROKER_HOST     (default: localhost)
 *   MQTT_BROKER_PORT     (default: 1883)
 *   MQTT_CLIENT_ID       (default: ioteste-generator)
 *   GENERATOR_INTERVAL_MS (default: 10000)
 */
public class EventGenerator {

    private static final Logger log = LoggerFactory.getLogger(EventGenerator.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();

    private static final List<SimulatedThermostat> THERMOSTATS = List.of(
            new SimulatedThermostat("ht-sim-room1", 0, 21.0),
            new SimulatedThermostat("ht-sim-room2", 0, 19.5)
    );

    public static void main(String[] args) {
        String host = getEnv("MQTT_BROKER_HOST", "localhost");
        String port = getEnv("MQTT_BROKER_PORT", "1883");
        String clientId = getEnv("MQTT_CLIENT_ID", "ioteste-generator");
        long intervalMs = Long.parseLong(getEnv("GENERATOR_INTERVAL_MS", "10000"));

        String brokerUrl = "tcp://" + host + ":" + port;

        log.info("=== IoTEste EcoWarm - Generador de Eventos ===");
        log.info("Broker: {}", brokerUrl);
        log.info("Termostatos simulados: {}", THERMOSTATS.size());
        log.info("Intervalo de publicación: {} ms", intervalMs);
        log.info("================================================");

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            log.info("Conectando al broker...");
            client.connect(options);
            log.info("Conectado. Iniciando publicación periódica...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("Cerrando conexión MQTT...");
                    client.disconnect();
                } catch (MqttException e) {
                    // Ignorado en el shutdown
                }
            }));

            while (true) {
                for (SimulatedThermostat thermostat : THERMOSTATS) {
                    publishReading(client, thermostat);
                }
                Thread.sleep(intervalMs);
            }

        } catch (MqttException e) {
            log.error("Error MQTT: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void publishReading(MqttClient client, SimulatedThermostat thermostat) {
        double variation = (random.nextDouble() - 0.5) * 0.6; // +/- 0.3 grados
        double tC = Math.round((thermostat.baseTempC() + variation) * 10.0) / 10.0;
        double tF = Math.round((tC * 9.0 / 5.0 + 32.0) * 10.0) / 10.0;
        long ts = System.currentTimeMillis();

        Map<String, Object> payload = Map.of(
                "id", thermostat.id(),
                "tC", tC,
                "tF", tF,
                "ts", ts
        );

        String topic = thermostat.thermostatId() + "/status/temperature:" + thermostat.id();

        try {
            String json = mapper.writeValueAsString(payload);
            MqttMessage message = new MqttMessage(json.getBytes());
            message.setQos(0);
            client.publish(topic, message);
            log.info("Publicado topic={} payload={}", topic, json);
        } catch (Exception e) {
            log.error("Error publicando evento para {}: {}", thermostat.thermostatId(), e.getMessage(), e);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private record SimulatedThermostat(String thermostatId, int id, double baseTempC) {
    }
}