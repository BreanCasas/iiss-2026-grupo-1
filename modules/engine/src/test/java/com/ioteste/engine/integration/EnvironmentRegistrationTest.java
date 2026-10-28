
package com.ioteste.engine.integration;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentRegistrationTest {

    private HttpServer server;
    private EnvironmentRegistration registration;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile boolean available;

    @BeforeEach
    void setUp() throws IOException {
        attempts.set(0);
        available = false;

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/controladores", exchange -> {
            attempts.incrementAndGet();

            if (!available) {
                byte[] body = "{\"error\":\"no disponible\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
                return;
            }

            byte[] body = """
                    {"mqtt":{"host":"127.0.0.1","puerto":1883}}
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);

            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });

        server.start();

        String url = "http://127.0.0.1:"
                + server.getAddress().getPort();

        registration = new EnvironmentRegistration(
                url, "test", "http://localhost:8080",
                "test-api-key", "");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesAfterRestFailureAndRegistersWhenServiceRecovers() {
        registration.register();

        assertNull(registration.broker());
        assertEquals(1, attempts.get());

        available = true;
        registration.register();

        assertNotNull(registration.broker());
        assertEquals("127.0.0.1", registration.broker().host());
        assertEquals(1883, registration.broker().puerto());
        assertEquals(2, attempts.get());
    }
}
