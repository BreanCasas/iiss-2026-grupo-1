package com.ioteste.core;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.ioteste.core.HeatingController.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatingDecisionTest {

    private final HeatingController controller =
            new HeatingController();

    @Test
    void peakTurnsOffBothColdRoomsThatWereOn() {
        assertEquals(
                List.of(off("room1"), off("room2")),
                decide(
                        3700L, 17, 0, true,
                        observation("room1", 19000L, SwitchState.ON),
                        observation("room2", 17000L, SwitchState.ON)
                )
        );
    }

    @Test
    void limitedPowerTransfersEnergyToHigherDeficitRoom() {
        assertEquals(
                List.of(off("room1"), on("room2")),
                decide(
                        1200L, 12, 0, true,
                        observation("room1", 20000L, SwitchState.ON),
                        observation("room2", 17000L, SwitchState.OFF)
                )
        );
    }

    @Test
    void reachingTargetTurnsOffOneRoomAndStartsTheOther() {
        assertEquals(
                List.of(off("room1"), on("room2")),
                decide(
                        3700L, 12, 0, true,
                        observation("room1", 21500L, SwitchState.ON),
                        observation("room2", 18000L, SwitchState.OFF)
                )
        );
    }

    @Test
    void alreadyCorrectStatesProduceNoActions() {
        assertEquals(
                List.of(),
                decide(
                        1200L, 12, 0, true,
                        observation("room1", 20000L, SwitchState.OFF),
                        observation("room2", 17000L, SwitchState.ON)
                )
        );
    }

    @Test
    void beforePeakDuringPeakAndAfterPeakProduceConsistentDecisions() {
        SiteConfig config = config(1200L);

        ControlState before = state(
                true,
                observation("room1", 20000L, SwitchState.OFF),
                observation("room2", 17000L, SwitchState.OFF)
        );

        assertEquals(
                List.of(on("room2")),
                controller.decide(
                        config,
                        monday(16, 59),
                        before
                ).actions()
        );

        // Estado después de aplicar el encendido anterior.
        ControlState during = state(
                true,
                observation("room1", 20000L, SwitchState.OFF),
                observation("room2", 17000L, SwitchState.ON)
        );

        assertEquals(
                List.of(off("room2")),
                controller.decide(
                        config,
                        monday(17, 0),
                        during
                ).actions()
        );

        // Después del apagado y nuevas lecturas, cambia la prioridad.
        ControlState after = state(
                true,
                observation("room1", 18000L, SwitchState.OFF),
                observation("room2", 19000L, SwitchState.OFF)
        );

        assertEquals(
                List.of(on("room1")),
                controller.decide(
                        config,
                        monday(23, 0),
                        after
                ).actions()
        );
    }

    @Test
    void unknownSwitchMustBeTurnedOffBeforeBeingTurnedOn() {
        assertEquals(
                List.of(off("room1"), on("room1")),
                decide(
                        3700L, 12, 0, true,
                        observation(
                                "room1",
                                19000L,
                                SwitchState.DESCONOCIDO
                        ),
                        observation("room2", 20000L, SwitchState.OFF)
                )
        );
    }

    @Test
    void missingTemperatureTurnsOffRoomInsteadOfSelectingIt() {
        assertEquals(
                List.of(off("room1")),
                decide(
                        3700L, 12, 0, true,
                        observation("room1", null, SwitchState.ON),
                        observation("room2", 20000L, SwitchState.OFF)
                )
        );
    }

    @Test
    void stoppedControlTurnsOffBothRoomsRegardlessOfTheirTemperatures() {
        ControlState stopped = state(
                false,
                observation("room1", 19000L, SwitchState.ON),
                observation("room2", 17000L, SwitchState.ON)
        );

        assertEquals(
                List.of(off("room1"), off("room2")),
                controller.decide(
                        config(3700L),
                        null,
                        stopped
                ).actions()
        );
    }

    private List<SwitchAction> decide(
            long power,
            int hour,
            int minute,
            boolean running,
            RoomObservation... observations) {

        return controller.decide(
                config(power),
                monday(hour, minute),
                state(running, observations)
        ).actions();
    }

    private SiteConfig config(long power) {
        return new SiteConfig(
                List.of(
                        new RoomConfig("room1", 21500L, 1200L),
                        new RoomConfig("room2", 20000L, 1200L)
                ),
                power,
                new PeakSchedule(
                        LocalTime.of(17, 0),
                        LocalTime.of(23, 0),
                        PeakSchedule.Days.HABILES
                )
        );
    }

    private ControlState state(
            boolean running,
            RoomObservation... rooms) {

        return new ControlState(
                running,
                List.of(rooms)
        );
    }

    private RoomObservation observation(
            String id,
            Long temperature,
            SwitchState status) {

        return new RoomObservation(
                id,
                temperature,
                status
        );
    }

    private LocalDateTime monday(int hour, int minute) {
        return LocalDateTime.of(
                2026, 10, 5, hour, minute
        );
    }

    private SwitchAction off(String id) {
        return new SwitchAction(id, SwitchState.OFF);
    }

    private SwitchAction on(String id) {
        return new SwitchAction(id, SwitchState.ON);
    }
}