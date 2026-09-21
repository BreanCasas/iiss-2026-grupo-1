package com.ioteste.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SwitchService {

    private final RestClient restClient;

    public SwitchService(
            RestClient.Builder restClientBuilder,
            @Value("${switch.stub.url}") String switchStubUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(switchStubUrl)
                .build();
    }

    public void turnOn(String switchId) {
        restClient.post()
                .uri("/switches/{id}/on", switchId)
                .retrieve()
                .toBodilessEntity();
    }

    public void turnOff(String switchId) {
        restClient.post()
                .uri("/switches/{id}/off", switchId)
                .retrieve()
                .toBodilessEntity();
    }
}