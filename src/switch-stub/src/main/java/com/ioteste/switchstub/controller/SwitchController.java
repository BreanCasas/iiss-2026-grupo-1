package com.ioteste.switchstub.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwitchController {

    private static final Logger log =
            LoggerFactory.getLogger(SwitchController.class);

    @PostMapping("/switches/{id}/on")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnOn(@PathVariable String id) {
        log.info("Switch {} -> ON", id);
    }

    @PostMapping("/switches/{id}/off")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnOff(@PathVariable String id) {
        log.info("Switch {} -> OFF", id);
    }
}