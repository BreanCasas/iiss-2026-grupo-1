package com.ioteste.core;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HeatingController {

    public boolean shouldHeat(
            double currentTemperature,
            double targetTemperature,
            double requiredPowerKw,
            double availablePowerKw,
            boolean peakHours) {

        return !peakHours && currentTemperature < targetTemperature && requiredPowerKw <= availablePowerKw;
    }

    public Set<String> selectRoomsToHeat(
            List<RoomState> rooms,
            double maximumPowerKw,
            boolean peakHours) {

        if (peakHours) {
            return Set.of();
        }

        List<RoomState> prioritizedRooms = rooms.stream()
                .filter(room ->
                        room.currentTemperature() < room.targetTemperature())
                .sorted(Comparator
                        .comparingDouble((RoomState room) ->
                                room.targetTemperature()
                                        - room.currentTemperature())
                        .reversed()
                        .thenComparing(RoomState::id))
                .toList();

        Set<String> selected = new LinkedHashSet<>();
        double availablePowerKw = maximumPowerKw;

        for (RoomState room : prioritizedRooms) {
            if (shouldHeat(
                    room.currentTemperature(),
                    room.targetTemperature(),
                    room.requiredPowerKw(),
                    availablePowerKw,
                    peakHours)) {

                selected.add(room.id());
                availablePowerKw -= room.requiredPowerKw();
            }
        }

        return Set.copyOf(selected);
    }
}