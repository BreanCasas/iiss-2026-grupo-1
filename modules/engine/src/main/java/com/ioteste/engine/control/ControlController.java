package com.ioteste.engine.control;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/control")
public class ControlController {

    private final ControlService service;

    public ControlController(ControlService service) {
        this.service = service;
    }

    @GetMapping
    public ControlStatus status() {
        return service.status();
    }

    @PostMapping("/comandos")
    public ControlStatus command(@RequestBody ControlCommand command) {
        return service.command(command);
    }
}