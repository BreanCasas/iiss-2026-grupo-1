package com.ioteste.api.controller;

import com.ioteste.api.model.ControllerState;
import com.ioteste.api.service.ControllerStateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/controller")
public class ControllerStateController {

    private final ControllerStateService controllerStateService;

    public ControllerStateController(
            ControllerStateService controllerStateService
    ) {
        this.controllerStateService = controllerStateService;
    }

    @PostMapping("/start")
    public ControllerState start() {
        return controllerStateService.start();
    }

    @PostMapping("/stop")
    public ControllerState stop() {
        return controllerStateService.stop();
    }

    @GetMapping("/status")
    public ControllerState status() {
        return controllerStateService.getState();
    }
}