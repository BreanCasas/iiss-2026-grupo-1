package com.ioteste.api.service;

import com.ioteste.api.model.Room;
import com.ioteste.api.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoomValidationService {

    private final RoomRepository roomRepository;

    public RoomValidationService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<String> validate() {
        List<Room> rooms = roomRepository.findAll();
        Set<String> thermostatIds = new HashSet<>();
        Set<String> switchIds = new HashSet<>();

        List<String> errors = new java.util.ArrayList<>();

        for (Room room : rooms) {

            if (room.getThermostatId() == null || room.getThermostatId().isBlank()) {
                errors.add("Habitación " + room.getId() + ": thermostatId faltante");
            } else if (!thermostatIds.add(room.getThermostatId())) {
                errors.add("thermostatId duplicado: " + room.getThermostatId());
            }

            if (room.getSwitchId() == null || room.getSwitchId().isBlank()) {
                errors.add("Habitación " + room.getId() + ": switchId faltante");
            } else if (!switchIds.add(room.getSwitchId())) {
                errors.add("switchId duplicado: " + room.getSwitchId());
            }
        }

        return errors;
    }
}