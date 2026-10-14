package com.ioteste.engine.site;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sitio")
public class SiteController {

    private final SiteService service;

    public SiteController(SiteService service) {
        this.service = service;
    }

    @GetMapping
    public SiteInventory get() {
        return service.get();
    }

    @PutMapping
    public SiteInventory replace(@RequestBody SiteInventory inventory) {
        return service.replace(inventory);
    }
}