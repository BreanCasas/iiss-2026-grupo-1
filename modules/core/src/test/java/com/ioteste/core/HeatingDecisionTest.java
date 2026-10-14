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
                        3.7, 17, 0, true,
                        observation("room1", 19.0, SwitchState.ON),
                        observation("room2", 17.0, SwitchState.ON)
                )
        );
    }

    @Test
    void limitedPowerTransfersEnergyToHigherDeficitRoom() {
        assertEquals(
                List.of(off("room1"), on("room2")),
                decide(
                        1.2, 12, 0, true,
                        observation("room1", 20.0, SwitchState.ON),
                        observation("room2", 17.0, SwitchState.OFF)
                )
        );
    }

    @Test
    void reachingTargetTurnsOffOneRoomAndStartsTheOther() {
        assertEquals(
                List.of(off("room1"), on("room2")),
                decide(
                        3.7, 12, 0, true,
                        observation("room1", 21.5, SwitchState.ON),
                        observation("room2", 18.0, SwitchState.OFF)
                )
        );
    }

    @Test
    void alreadyCorrectStatesProduceNoActions() {
        assertEquals(
                List.of(),
                decide(
                        1.2, 12, 0, true,
                        observation("room1", 20.0, SwitchState.OFF),
                        observation("room2", 17.0, SwitchState.ON)
                )
        );
    }

    @Test
    void beforePeakDuringPeakAndAfterPeakProduceConsistentDecisions() {
        SiteConfig config = config(1.2);

        ControlState before = state(
                true,
                observation("room1", 20.0, SwitchState.OFF),
                observation("room2", 17.0, SwitchState.OFF)
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
                observation("room1", 20.0, SwitchState.OFF),
                observation("room2", 17.0, SwitchState.ON)
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
                observation("room1", 18.0, SwitchState.OFF),
                observation("room2", 19.0, SwitchState.OFF)
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
                        3.7, 12, 0, true,
                        observation(
                                "room1",
                                19.0,
                                SwitchState.DESCONOCIDO
                        ),
                        observation("room2", 20.0, SwitchState.OFF)
                )
        );
    }

    @Test
    void missingTemperatureTurnsOffRoomInsteadOfSelectingIt() {
        assertEquals(
                List.of(off("room1")),
                decide(
                        3.7, 12, 0, true,
                        observation("room1", null, SwitchState.ON),
                        observation("room2", 20.0, SwitchState.OFF)
                )
        );
    }

    @Test
    void stoppedControlTurnsOffBothRoomsRegardlessOfTheirTemperatures() {
        ControlState stopped = state(
                false,
                observation("room1", 19.0, SwitchState.ON),
                observation("room2", 17.0, SwitchState.ON)
        );

        assertEquals(
                List.of(off("room1"), off("room2")),
                controller.decide(
                        config(3.7),
                        null,
                        stopped
                ).actions()
        );
    }

    private List<SwitchAction> decide(
            double power,
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

    private SiteConfig config(double power) {
        return new SiteConfig(
                List.of(
                        new RoomConfig("room1", 21.5, 1.2),
                        new RoomConfig("room2", 20.0, 1.2)
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
            Double temperature,
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