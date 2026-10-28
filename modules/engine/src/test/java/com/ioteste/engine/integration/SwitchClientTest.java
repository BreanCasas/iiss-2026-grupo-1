package com.ioteste.engine.integration;

import com.ioteste.engine.site.SiteInventory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SwitchClientTest {

    private HttpServer server;
    private SwitchClient client;
    private SiteInventory.Room room;
    private final AtomicReference<Integer> statusCode = new AtomicReference<>(200);
    private final AtomicReference<String> response =
            new AtomicReference<>("{\"estado\":\"ON\"}");
    private final AtomicReference<String> requestBody =
            new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/switches/sw-room1", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }

            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            int code = statusCode.get();

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");

            exchange.sendResponseHeaders(
                    code, code == 204 ? -1 : body.length);

            if (code != 204) {
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            } else {
                exchange.close();
            }
        });
        server.start();

        client = new SwitchClient("");
        room = new SiteInventory.Room(
                "room1", "Living", 21500L, 1200L,
                "ht-room1", "ht-room1/status/temperature:0",
                "sw-room1", "http://127.0.0.1:" +
                        server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void statusReturnsStateReportedBySwitch() {
        response.set("{\"estado\":\"ON\"}");

        assertEquals(SwitchClient.State.ON, client.status(room));
    }

    @Test
    void commandReturnsConfirmedState() {
        response.set("{\"estado\":\"OFF\"}");

        assertEquals(
                SwitchClient.State.OFF,
                client.command(room, SwitchClient.State.OFF));
        assertTrue(requestBody.get().contains("\"accion\":\"OFF\""));
    }

    @Test
    void httpErrorIsPropagated() {
        statusCode.set(503);
        response.set("{\"error\":\"servicio no disponible\"}");

        assertThrows(
                RuntimeException.class,
                () -> client.status(room));
    }

    @Test
    void missingStateInResponseIsRejected() {
        response.set("{\"mensaje\":\"sin estado\"}");

        assertThrows(
                IllegalStateException.class,
                () -> client.status(room));
    }

    @Test
    void differentConfirmedStateIsRejected() {
        response.set("{\"estado\":\"ON\"}");

        assertThrows(
                IllegalStateException.class,
                () -> client.command(room, SwitchClient.State.OFF));
    }

    @Test
    void unknownStateCannotBeCommanded() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.command(room, SwitchClient.State.DESCONOCIDO));
    }
}
