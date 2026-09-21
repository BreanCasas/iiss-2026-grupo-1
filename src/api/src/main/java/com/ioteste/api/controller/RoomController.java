package com.ioteste.api.controller;

import com.ioteste.api.model.Room;
import com.ioteste.api.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.ioteste.api.model.TemperatureReading;
import com.ioteste.api.repository.TemperatureReadingRepository;
import org.springframework.web.bind.annotation.PatchMapping;
import com.ioteste.api.service.SwitchService;
import com.ioteste.api.service.RoomValidationService;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final TemperatureReadingRepository temperatureReadingRepository;
    private final SwitchService switchService;
    private final RoomValidationService roomValidationService;

    public RoomController(
            RoomRepository roomRepository,
            TemperatureReadingRepository temperatureReadingRepository,
            SwitchService switchService,
            RoomValidationService roomValidationService
    ) {
        this.roomRepository = roomRepository;
        this.temperatureReadingRepository = temperatureReadingRepository;
        this.switchService = switchService;
        this.roomValidationService = roomValidationService;
    }

    @GetMapping
    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    @GetMapping("/{id}")
    public Room findById(@PathVariable String id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Habitación no encontrada"
                ));
    }

    @GetMapping("/{id}/readings")
    public List<TemperatureReading> findReadings(@PathVariable String id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Habitación no encontrada"
            );
        }

        return temperatureReadingRepository.findByRoomId(id);
    }

    @PostMapping("/{id}/switch/on")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnSwitchOn(@PathVariable String id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Habitación no encontrada"
                ));

        switchService.turnOn(room.getSwitchId());
    }

    @PostMapping("/{id}/switch/off")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void turnSwitchOff(@PathVariable String id) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Habitación no encontrada"
                ));

        switchService.turnOff(room.getSwitchId());
    }

    @PostMapping("/validate")
    public List<String> validateRooms() {
        return roomValidationService.validate();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room create(@RequestBody Room room) {
        return roomRepository.save(room);
    }

    @PutMapping("/{id}")
    public Room update(@PathVariable String id, @RequestBody Room room) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Habitación no encontrada"
            );
        }

        room.setId(id);
        return roomRepository.save(room);
    }

    @PatchMapping("/{id}")
    public Room partialUpdate(@PathVariable String id, @RequestBody Room changes) {

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Habitación no encontrada"
                ));

        if (changes.getName() != null) {
            room.setName(changes.getName());
        }

        if (changes.getThermostatId() != null) {
            room.setThermostatId(changes.getThermostatId());
        }

        if (changes.getSwitchId() != null) {
            room.setSwitchId(changes.getSwitchId());
        }

        if (changes.getTargetTempC() != null) {
            room.setTargetTempC(changes.getTargetTempC());
        }

        return roomRepository.save(room);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Habitación no encontrada"
            );
        }

        roomRepository.deleteById(id);
    }
}