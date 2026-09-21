package com.ioteste.api.service;

import com.ioteste.api.model.ControllerState;
import com.ioteste.api.repository.ControllerStateRepository;
import org.springframework.stereotype.Service;

@Service
public class ControllerStateService {

    private static final String CONTROLLER_ID = "controller";

    private final ControllerStateRepository repository;

    public ControllerStateService(ControllerStateRepository repository) {
        this.repository = repository;
    }

    public ControllerState start() {
        ControllerState state = new ControllerState(CONTROLLER_ID, true);
        return repository.save(state);
    }

    public ControllerState stop() {
        ControllerState state = new ControllerState(CONTROLLER_ID, false);
        return repository.save(state);
    }

    public ControllerState getState() {
        return repository.findById(CONTROLLER_ID)
                .orElse(new ControllerState(CONTROLLER_ID, false));
    }
}