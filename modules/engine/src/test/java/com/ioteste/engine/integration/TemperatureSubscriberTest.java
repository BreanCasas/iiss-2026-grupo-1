package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.core.PeakSchedule;
import com.ioteste.engine.site.SiteInventory;
import com.ioteste.engine.site.SiteService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemperatureSubscriberTest {

    private static final String TOPIC =
            "ht-room1/status/temperature:0";

    private TemperatureReadingRepository readings;
    private TemperatureSubscriber subscriber;
    private SiteInventory.Room room;
    private Clock clock;

    @BeforeEach
    void setUp() {
        EnvironmentRegistration registration =
                mock(EnvironmentRegistration.class);

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

    private MqttMessage message(String json) {
        return new MqttMessage(
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}