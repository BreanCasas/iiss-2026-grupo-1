package com.ioteste.subscriber.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SwitchClient {

    private final String baseUrl;
    private final HttpClient httpClient;

    public SwitchClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void turnOn(String switchId) throws IOException, InterruptedException {
        sendCommand(switchId, "on");
    }

    public void turnOff(String switchId) throws IOException, InterruptedException {
        sendCommand(switchId, "off");
    }

    private void sendCommand(String switchId, String command)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                        baseUrl + "/switches/" + switchId + "/" + command
                ))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.discarding()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Switch respondió HTTP " + response.statusCode()
            );
        }
    }
}