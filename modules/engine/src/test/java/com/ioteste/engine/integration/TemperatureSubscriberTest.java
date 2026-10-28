package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemperatureSubscriberTest {

    private static final String TOPIC =
            "ht-room1/status/temperature:0";

    private EnvironmentRegistration registration;
    private TemperatureReadingRepository readings;
    private TemperatureSubscriber subscriber;
    private SiteInventory.Room room;
    private Clock clock;

    @BeforeEach
    void setUp() {
        registration = mock(EnvironmentRegistration.class);

        SiteService siteService = mock(SiteService.class);
        readings = mock(TemperatureReadingRepository.class);

        room = new SiteInventory.Room(
                "room1",
                "Living",
                21500L,
                1200L,
                "ht-room1",
                TOPIC,
                "sw-room1",
                "http://localhost:9090"
        );

        SiteInventory inventory = new SiteInventory(
                "2.0",
                new SiteInventory.Site(
                        "casa",
                        "Casa",
                        3700L,
                        new SiteInventory.Tariff(
                                new SiteInventory.PeakPeriod(
                                        "17:00",
                                        "23:00",
                                        PeakSchedule.Days.HABILES
                                )
                        )
                ),
                List.of(room)
        );

        when(siteService.get()).thenReturn(inventory);

        clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1_000L);

        subscriber = new TemperatureSubscriber(
                registration,
                siteService,
                new ObjectMapper(),
                readings,
                clock
        );

        ReflectionTestUtils.setField(
                subscriber,
                "activeTopics",
                Set.of(TOPIC)
        );
    }

    @Test
    void validReadingUpdatesMemoryAndIsSentToRepository() {
        subscriber.receive(
                TOPIC,
                message("""
                        {
                          "id": 0,
                          "tC": 19.3,
                          "tF": 66.74,
                          "ts": 1791320400.5
                        }
                        """)
        );

        assertEquals(19300L, subscriber.temperature(TOPIC));

        ArgumentCaptor<JsonNode> payload =
                ArgumentCaptor.forClass(JsonNode.class);

        verify(readings).save(
                eq(room),
                eq(TOPIC),
                payload.capture(),
                any(Instant.class)
        );

        assertEquals(
                19300L,
                payload.getValue().get("tC").decimalValue()
                        .movePointRight(3).longValueExact()
        );

        assertTrue(payload.getValue().get("ts").isLong());

        assertEquals(
                1791320400500L,
                payload.getValue().get("ts").longValue()
        );
    }

    @Test
    void invalidTemperatureIsNotStored() {
        subscriber.receive(
                TOPIC,
                message("""
                        {"tC": "incorrecta"}
                        """)
        );

        assertNull(subscriber.temperature(TOPIC));
        verifyNoInteractions(readings);
    }

    @Test
    void unrelatedTopicIsIgnored() {
        subscriber.receive(
                "otro/status/temperature:0",
                message("""
                        {"tC": 19.3}
                        """)
        );

        assertNull(subscriber.temperature(
                "otro/status/temperature:0"
        ));

        verifyNoInteractions(readings);
    }

    @Test
    void persistenceFailureDoesNotDiscardLatestTemperature() {
        doThrow(new IllegalStateException("Mongo inaccesible"))
                .when(readings)
                .save(
                        any(SiteInventory.Room.class),
                        anyString(),
                        any(JsonNode.class),
                        any(Instant.class)
                );

        assertDoesNotThrow(() -> subscriber.receive(
                TOPIC,
                message("""
                        {"tC": 19.3}
                        """)
        ));

        assertEquals(19300L, subscriber.temperature(TOPIC));

        verify(readings).save(
                eq(room),
                eq(TOPIC),
                any(JsonNode.class),
                any(Instant.class)
        );
    }

    @Test
    void readingExpiresAfterThirtySecondsAndRecoversWithNewMessage() {
        subscriber.receive(TOPIC, message("{\"tC\":19.3}"));

        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(30_999L);

        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(31_000L);

        assertNull(subscriber.temperature(TOPIC));

        subscriber.receive(TOPIC, message("{\"tC\":18.5}"));

        assertEquals(18500L, subscriber.temperature(TOPIC));
    }

    @Test
    void invalidMessageDoesNotExtendReadingLifetime() {
        subscriber.receive(TOPIC, message("{\"tC\":19.3}"));

        when(clock.millis()).thenReturn(30_000L);

        subscriber.receive(TOPIC, message("{\"tC\":\"incorrecta\"}"));

        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(31_000L);

        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void briefInterruptionKeepsReadingValidAndNewMessageRenewsIt() {
        subscriber.receive(TOPIC, message("{\"tC\":19.3}"));

        when(clock.millis()).thenReturn(21_000L);

        assertEquals(19300L, subscriber.temperature(TOPIC));

        subscriber.receive(TOPIC, message("{\"tC\":19.5}"));

        when(clock.millis()).thenReturn(31_000L);

        assertEquals(19500L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(50_999L);

        assertEquals(19500L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(51_000L);

        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void realBrokerRestartAllowsSubscriptionAndReadingRecovery()
            throws Exception {

        try (GenericContainer<?> broker = broker()) {
            broker.start();

            connectSubscriber(broker);

            MqttClient originalClient = mqttClient();

            publish(broker, "{\"tC\":19.3}");
            awaitTemperature(19300L);

            var stopped = broker.execInContainer(
                    "sh",
                    "-c",
                    "kill -TERM $(pidof mosquitto)"
            );

            assertEquals(
                    0,
                    stopped.getExitCode(),
                    stopped.getStderr()
            );

            await()
                    .atMost(Duration.ofSeconds(20))
                    .until(() -> !originalClient.isConnected());

            when(clock.millis()).thenReturn(31_000L);

            assertNull(subscriber.temperature(TOPIC));

            var restarted = broker.execInContainer(
                    "mosquitto",
                    "-c",
                    "/mosquitto/config/test.conf",
                    "-d"
            );

            assertEquals(
                    0,
                    restarted.getExitCode(),
                    restarted.getStderr()
            );

            await()
                    .atMost(Duration.ofSeconds(40))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        subscriber.synchronizeSubscriptions();
                        assertTrue(originalClient.isConnected());
                        assertTrue(subscribedTopics().contains(TOPIC));
                    });

            assertSame(originalClient, mqttClient());

            publish(broker, "{\"tC\":18.5}");
            awaitTemperature(18500L);

        } finally {
            subscriber.close();
        }
    }

    @Test
    void publicationsMissedWithoutSubscriptionDoNotRenewTheReading()
            throws Exception {

        try (GenericContainer<?> broker = broker()) {
            broker.start();

            connectSubscriber(broker);

            publish(broker, "{\"tC\":19.3}");
            awaitTemperature(19300L);

            MqttClient client = mqttClient();
            client.unsubscribe(TOPIC);

            when(clock.millis()).thenReturn(21_000L);

            publish(broker, "{\"tC\":18.1}");
            publish(broker, "{\"tC\":18.2}");

            assertEquals(19300L, subscriber.temperature(TOPIC));

            when(clock.millis()).thenReturn(31_000L);

            assertNull(subscriber.temperature(TOPIC));

            subscribedTopics().clear();
            subscriber.synchronizeSubscriptions();

            assertTrue(client.isConnected());
            assertTrue(subscribedTopics().contains(TOPIC));

            publish(broker, "{\"tC\":18.5}");
            awaitTemperature(18500L);

            ArgumentCaptor<JsonNode> payload =
                    ArgumentCaptor.forClass(JsonNode.class);

            await()
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() ->
                            verify(readings, times(2)).save(
                                    eq(room),
                                    eq(TOPIC),
                                    any(JsonNode.class),
                                    any(Instant.class)
                            )
                    );

            verify(readings, times(2)).save(
                    eq(room),
                    eq(TOPIC),
                    payload.capture(),
                    any(Instant.class)
            );

            List<Long> storedTemperatures = payload.getAllValues()
                    .stream()
                    .map(value -> value.get("tC").decimalValue()
                            .movePointRight(3).longValueExact())
                    .toList();

            assertEquals(
                    List.of(19300L, 18500L),
                    storedTemperatures
            );

        } finally {
            subscriber.close();
        }
    }

    private GenericContainer<?> broker() {
        return new GenericContainer<>("eclipse-mosquitto:2.0.22")
                .withExposedPorts(1883)
                .withCopyToContainer(
                        Transferable.of("""
                                listener 1883
                                allow_anonymous true
                                persistence false
                                """),
                        "/mosquitto/config/test.conf"
                )
                .withCommand(
                        "sh",
                        "-c",
                        "mosquitto -c /mosquitto/config/test.conf -d"
                                + " && exec tail -f /dev/null"
                );
    }

    private void connectSubscriber(GenericContainer<?> broker) {
        when(registration.broker()).thenReturn(
                new EnvironmentRegistration.Broker(
                        broker.getHost(),
                        broker.getMappedPort(1883)
                )
        );

        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    subscriber.synchronizeSubscriptions();
                    assertNotNull(mqttClient());
                    assertTrue(mqttClient().isConnected());
                    assertTrue(subscribedTopics().contains(TOPIC));
                });
    }

    private void publish(GenericContainer<?> broker, String json)
            throws Exception {

        MqttClient publisher = new MqttClient(
                "tcp://" + broker.getHost()
                        + ":" + broker.getMappedPort(1883),
                MqttClient.generateClientId(),
                new MemoryPersistence()
        );

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setConnectionTimeout(5);
            options.setCleanSession(true);

            publisher.setTimeToWait(10_000L);
            publisher.connect(options);

            MqttMessage publication = message(json);
            publication.setQos(1);
            publication.setRetained(false);

            publisher.publish(TOPIC, publication);

        } finally {
            try {
                if (publisher.isConnected()) {
                    publisher.disconnect();
                }
            } finally {
                publisher.close();
            }
        }
    }

    private void awaitTemperature(long expected) {
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                        assertEquals(expected, subscriber.temperature(TOPIC))
                );
    }

    private MqttClient mqttClient() {
        return (MqttClient) ReflectionTestUtils.getField(
                subscriber,
                "client"
        );
    }

    @SuppressWarnings("unchecked")
    private Set<String> subscribedTopics() {
        return (Set<String>) ReflectionTestUtils.getField(
                subscriber,
                "subscribedTopics"
        );
    }

    private MqttMessage message(String json) {
        return new MqttMessage(
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}