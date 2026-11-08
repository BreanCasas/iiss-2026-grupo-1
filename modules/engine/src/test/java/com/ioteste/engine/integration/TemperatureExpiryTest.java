package com.ioteste.engine.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ioteste.engine.control.ControlService;
import com.ioteste.engine.control.ControlStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemperatureExpiryTest {

    private static final String TOPIC = "ht-room1/status/temperature:0";

    private Clock clock;
    private ControlService control;
    private TemperatureSubscriber subscriber;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);
        control = mock(ControlService.class);
        SiteService site = mock(SiteService.class);

        SiteInventory.Room room = new SiteInventory.Room(
                "room1", "Living", 21500L, 1200L,
                "ht-room1", TOPIC, "sw-room1", "http://localhost:9090");

        when(site.get()).thenReturn(
                new SiteInventory("2.0", null, List.of(room)));
        when(clock.millis()).thenReturn(1000L);
        running(60000L);

        subscriber = new TemperatureSubscriber(
                mock(EnvironmentRegistration.class),
                site,
                new ObjectMapper(),
                mock(TemperatureReadingRepository.class),
                clock,
                control
        );

        ReflectionTestUtils.setField(
                subscriber, "activeTopics", Set.of(TOPIC));

        receive("{\"tC\":19.3}");
    }

    @Test
    void factorSixtyExpiresReadingAfterFiveRealSeconds() {
        when(clock.millis()).thenReturn(5999L);
        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(6000L);
        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void normalSpeedKeepsThirtySecondRealLimit() {
        running(1000L);

        when(clock.millis()).thenReturn(30999L);
        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(31000L);
        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void freshReadingRestoresTemperatureAfterExpiry() {
        when(clock.millis()).thenReturn(6000L);
        assertNull(subscriber.temperature(TOPIC));

        receive("{\"tC\":18.5}");
        assertEquals(18500L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(11000L);
        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void invalidReadingDoesNotExtendAcceleratedLifetime() {
        when(clock.millis()).thenReturn(5500L);
        receive("{\"tC\":\"incorrecta\"}");

        when(clock.millis()).thenReturn(6000L);
        assertNull(subscriber.temperature(TOPIC));
    }

    @Test
    void stoppedControlKeepsThirtySecondRealLimit() {
        when(control.status()).thenReturn(ControlStatus.stopped());

        when(clock.millis()).thenReturn(30999L);
        assertEquals(19300L, subscriber.temperature(TOPIC));

        when(clock.millis()).thenReturn(31000L);
        assertNull(subscriber.temperature(TOPIC));
    }

    private void running(long factor) {
        when(control.status()).thenReturn(new ControlStatus(
                ControlStatus.State.EN_EJECUCION,
                Instant.parse("2026-11-03T13:00:00Z"),
                factor,
                Instant.parse("2026-11-07T23:00:00Z")
        ));
    }

    private void receive(String payload) {
        subscriber.receive(TOPIC, new MqttMessage(
                payload.getBytes(StandardCharsets.UTF_8)));
    }
}