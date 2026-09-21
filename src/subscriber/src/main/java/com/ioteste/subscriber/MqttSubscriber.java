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
import com.ioteste.subscriber.client.SwitchClient;
import com.ioteste.subscriber.repository.ControllerStateRepository;

import java.io.IOException;
import java.time.Instant;

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

        String mongoUri = getRequiredEnv("MONGODB_URI");
        String mongoDatabase = getEnv("MONGODB_DATABASE", "ioteste");
        String switchStubUrl = getEnv("SWITCH_STUB_URL", "http://localhost:8081");

        String brokerUrl = "tcp://" + host + ":" + port;

        RoomRepository roomRepository = new RoomRepository(mongoUri, mongoDatabase);
        TemperatureReadingRepository readingRepository = new TemperatureReadingRepository(mongoUri, mongoDatabase);
        SwitchClient switchClient = new SwitchClient(switchStubUrl);
        ControllerStateRepository controllerStateRepository = new ControllerStateRepository(mongoUri, mongoDatabase);

        log.info("=== IoTEste EcoWarm - Consumidor de Eventos ===");
        log.info("Broker: {}", brokerUrl);
        log.info("Topic : {}", topic);
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
                    handleMessage(receivedTopic, message, roomRepository, readingRepository, controllerStateRepository, switchClient);
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
                    roomRepository.close();
                    controllerStateRepository.close();
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
            RoomRepository roomRepository,
            TemperatureReadingRepository readingRepository,
            ControllerStateRepository controllerStateRepository,
            SwitchClient switchClient
    ) {
        String payload = new String(message.getPayload());
        log.info("topic={} payload={}", receivedTopic, payload);

        try {
            JsonNode json = mapper.readTree(payload);
            double tC = json.get("tC").asDouble();
            double tF = json.get("tF").asDouble();
            long ts = json.get("ts").asLong();

            String thermostatId = extractThermostatId(receivedTopic);
            Room room = roomRepository.findByThermostatId(thermostatId);

            if (room == null) {
                log.warn(
                        "No se encontró habitación asociada al termostato '{}' (topic={}). Lectura descartada.",
                        thermostatId,
                        receivedTopic
                );
                return;
            }

            // Primero se persiste SIEMPRE la lectura recibida.
            TemperatureReading reading = new TemperatureReading(
                    room.id(),
                    tC,
                    tF,
                    ts,
                    Instant.now()
            );

            boolean persisted = readingRepository.append(reading);

            if (persisted) {
                log.info(
                        "Lectura persistida: room={} tC={} tF={}",
                        room.id(),
                        tC,
                        tF
                );
            } else {
                log.warn(
                        "La lectura no pudo persistirse: room={} tC={} tF={}",
                        room.id(),
                        tC,
                        tF
                );
            }

            // El switch solo se controla si el controlador está iniciado.
            if (!controllerStateRepository.isEnabled()) {
                log.info(
                        "Controlador detenido. No se acciona el switch de la habitación {}.",
                        room.id()
                );
                return;
            }

            try {
                if (tC < room.targetTempC()) {
                    log.info(
                            "Temperatura {}°C < objetivo {}°C. Encendiendo switch {}.",
                            tC,
                            room.targetTempC(),
                            room.switchId()
                    );

                    switchClient.turnOn(room.switchId());

                } else {
                    log.info(
                            "Temperatura {}°C >= objetivo {}°C. Apagando switch {}.",
                            tC,
                            room.targetTempC(),
                            room.switchId()
                    );

                    switchClient.turnOff(room.switchId());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(
                        "Interrumpido al enviar comando al switch {}",
                        room.switchId()
                );

            } catch (IOException e) {
                log.error(
                        "Error enviando comando al switch {}: {}",
                        room.switchId(),
                        e.getMessage()
                );
            }

        } catch (IOException e) {
            log.error(
                    "No se pudo parsear el payload como JSON: {}",
                    e.getMessage()
            );
        } catch (NullPointerException e) {
            log.error(
                    "Payload con formato inesperado (faltan campos tC/tF/ts): {}",
                    payload
            );
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