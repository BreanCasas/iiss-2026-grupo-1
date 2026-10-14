package com.ioteste.core;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeatingController {

    public enum SwitchState {
        ON,
        OFF,
        DESCONOCIDO
    }

    public record RoomConfig(
            String id,
            double targetTemperature,
            double requiredPowerKw
    ) {
    }

    public record SiteConfig(
            List<RoomConfig> rooms,
            double maximumPowerKw,
            PeakSchedule peakSchedule
    ) {
        public SiteConfig {
            rooms = List.copyOf(rooms);
        }
    }

    public record RoomObservation(
            String id,
            Double currentTemperature,
            SwitchState switchState
    ) {
    }

    public record ControlState(
            boolean running,
            List<RoomObservation> rooms
    ) {
        public ControlState {
            rooms = List.copyOf(rooms);
        }
    }

    public record SwitchAction(
            String roomId,
            SwitchState state
    ) {
    }

    public record Decision(List<SwitchAction> actions) {
        public Decision {
            actions = List.copyOf(actions);
        }
    }

    public Decision decide(
            SiteConfig config,
            LocalDateTime time,
            ControlState state) {

        Map<String, RoomObservation> observed = new HashMap<>();

        for (RoomObservation room : state.rooms()) {
            observed.put(room.id(), room);
        }

        List<RoomState> measured = new ArrayList<>();

        for (RoomConfig room : config.rooms()) {
            RoomObservation observation = observed.get(room.id());

            if (observation != null
                    && observation.currentTemperature() != null) {

                measured.add(new RoomState(
                        room.id(),
                        observation.currentTemperature(),
                        room.targetTemperature(),
                        room.requiredPowerKw()
                ));
            }
        }

        Set<String> selected = state.running()
                ? selectRoomsToHeat(
                measured,
                config.maximumPowerKw(),
                config.peakSchedule().isPeak(time)
        )
                : Set.of();

        List<SwitchAction> actions = new ArrayList<>();

        // Primero todos los apagados necesarios.
        for (RoomConfig room : config.rooms()) {
            SwitchState current =
                    switchState(observed.get(room.id()));

            if (current == SwitchState.DESCONOCIDO
                    || (!selected.contains(room.id())
                    && current != SwitchState.OFF)) {

                actions.add(new SwitchAction(
                        room.id(),
                        SwitchState.OFF
                ));
            }
        }

        // Después los encendidos necesarios.
        for (RoomConfig room : config.rooms()) {
            if (selected.contains(room.id())
                    && switchState(observed.get(room.id()))
                    != SwitchState.ON) {

                actions.add(new SwitchAction(
                        room.id(),
                        SwitchState.ON
                ));
            }
        }

        return new Decision(actions);
    }

    private SwitchState switchState(RoomObservation observation) {
        return observation == null || observation.switchState() == null
                ? SwitchState.DESCONOCIDO
                : observation.switchState();
    }

    public boolean shouldHeat(
            double currentTemperature,
            double targetTemperature,
            double requiredPowerKw,
            double availablePowerKw,
            boolean peakHours) {

        return !peakHours
                && currentTemperature < targetTemperature
                && requiredPowerKw <= availablePowerKw;
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
                        room.currentTemperature()
                                < room.targetTemperature())
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