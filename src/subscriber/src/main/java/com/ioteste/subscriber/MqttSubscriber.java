package com.ioteste.subscriber;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.subscriber.model.Room;
import com.ioteste.subscriber.model.TemperatureReading;
import com.ioteste.subscriber.repository.RoomRepository;
import com.ioteste.subscriber.repository.TemperatureReadingRepository;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consumidor de eventos de temperatura — extensión del suscriptor
 * MQTT mínimo de la Iteración 1.
 *
 * Se conecta al broker Mosquitto, se suscribe a los topics de
 * termostatos simulados, despliega cada evento en consola (vía logger)
 * y además persiste cada lectura en el histórico de la habitación
 * correspondiente.
 *
 * El mensaje esperado tiene el formato:
 *   Topic:   ht-sim-room1/status/temperature:0
 *   Payload: {"id":0,"tC":21.4,"tF":70.5,"ts":1789780929572}
 *
 * Configuración vía variables de entorno:
 *   MQTT_BROKER_HOST   (default: localhost)
 *   MQTT_BROKER_PORT   (default: 1883)
 *   MQTT_TOPIC         (default: ht-sim-+/status/temperature:+)
 *   MQTT_CLIENT_ID     (default: ioteste-subscriber)
 *   ROOMS_FILE         (default: /data/rooms.json)
 *   MONGODB_URI        (obligatoria)
 *   MONGODB_DATABASE   (default: ioteste)
 */
public class MqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        String host = getEnv("MQTT_BROKER_HOST", "localhost");
        String port = getEnv("MQTT_BROKER_PORT", "1883");
        String topic = getEnv("MQTT_TOPIC", "+/status/#");
        String clientId = getEnv("MQTT_CLIENT_ID", "ioteste-subscriber");
        Path roomsFile = Path.of(getEnv("ROOMS_FILE", "/data/rooms.json"));

        String mongoUri = getRequiredEnv("MONGODB_URI");
        String mongoDatabase = getEnv("MONGODB_DATABASE", "ioteste");

        String brokerUrl = "tcp://" + host + ":" + port;

        RoomRepository roomRepository = new RoomRepository(roomsFile);
        TemperatureReadingRepository readingRepository = new TemperatureReadingRepository(mongoUri, mongoDatabase);
        List<Room> rooms = roomRepository.findAll();

        log.info("=== IoTEste EcoWarm - Consumidor de Eventos ===");
        log.info("Broker: {}", brokerUrl);
        log.info("Topic : {}", topic);
        log.info("Habitaciones cargadas: {}", rooms.size());
        log.info("================================================");

        try {
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("Conexión perdida: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String receivedTopic, MqttMessage message) {
                    handleMessage(receivedTopic, message, rooms, readingRepository);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No aplica: este componente solo consume mensajes, no publica.
                }
            });

            log.info("Conectando al broker...");
            client.connect(options);
            log.info("Conectado. Suscribiendo a: {}", topic);

            client.subscribe(topic);
            log.info("Suscripción activa. Esperando eventos de temperatura...");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("Cerrando conexión MQTT...");
                    client.disconnect();
                } catch (MqttException e) {
                    // Ignorado en el shutdown
                } finally {
                    log.info("Cerrando conexión MongoDB...");
                    readingRepository.close();
                }
            }));

            Thread.currentThread().join();

        } catch (MqttException e) {
            log.error("Error MQTT: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Procesa un mensaje entrante: lo despliega en el log, identifica
     * a qué habitación corresponde (según el prefijo del topic y el
     * thermostatId configurado) y persiste la lectura si corresponde
     * a una habitación conocida.
     */
    private static void handleMessage(
            String receivedTopic,
            MqttMessage message,
            List<Room> rooms,
            TemperatureReadingRepository readingRepository
    ) {
        String payload = new String(message.getPayload());
        log.info("topic={} payload={}", receivedTopic, payload);

        try {
            JsonNode json = mapper.readTree(payload);
            double tC = json.get("tC").asDouble();
            double tF = json.get("tF").asDouble();
            long ts = json.get("ts").asLong();

            String thermostatId = extractThermostatId(receivedTopic);
            Optional<Room> room = findRoomByThermostatId(rooms, thermostatId);

            if (room.isEmpty()) {
                log.warn("No se encontró habitación asociada al termostato '{}' (topic={}). Lectura descartada.",
                        thermostatId, receivedTopic);
                return;
            }

            TemperatureReading reading = new TemperatureReading(
                    room.get().id(),
                    tC,
                    tF,
                    ts,
                    Instant.now()
            );

            boolean persisted = readingRepository.append(reading);

            if (persisted) {
                log.info(
                        "Lectura persistida: room={} tC={} tF={}",
                        room.get().id(),
                        tC,
                        tF
                );
            } else {
                log.warn(
                        "La lectura no pudo persistirse: room={} tC={} tF={}",
                        room.get().id(),
                        tC,
                        tF
                );
            }

        } catch (IOException e) {
            log.error("No se pudo parsear el payload como JSON: {}", e.getMessage());
        } catch (NullPointerException e) {
            log.error("Payload con formato inesperado (faltan campos tC/tF/ts): {}", payload);
        }
    }

    /**
     * Extrae el identificador del termostato a partir del topic.
     * Ejemplo: "ht-sim-room1/status/temperature:0" -> "ht-sim-room1"
     */
    private static String extractThermostatId(String topic) {
        int slashIndex = topic.indexOf('/');
        return slashIndex >= 0 ? topic.substring(0, slashIndex) : topic;
    }

    /**
     * Busca, entre las habitaciones configuradas, aquella cuyo
     * thermostatId coincida con el identificador recibido.
     */
    private static Optional<Room> findRoomByThermostatId(List<Room> rooms, String thermostatId) {
        return rooms.stream()
                .filter(r -> r.thermostatId().equals(thermostatId))
                .findFirst();
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static String getRequiredEnv(String key) {
        String value = System.getenv(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variable de entorno obligatoria no definida: " + key
            );
        }

        return value;
    }
}