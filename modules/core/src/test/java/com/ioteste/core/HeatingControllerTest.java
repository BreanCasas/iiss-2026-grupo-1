package com.ioteste.core;


import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HeatingControllerTest {

    @Test
    void heatsColdRoomWhenPowerIsAvailableOutsidePeakHours() {
        HeatingController controller = new HeatingController();

        boolean result = controller.shouldHeat(
                19000L,
                21500L,
                1200L,
                3700L,
                false
        );

        assertTrue(result,
                "Debe calefaccionar una habitación fría si hay potencia y no es horario punta");
    }

    @Test
    void doesNotHeatDuringPeakHoursEvenWhenRoomIsCold() {
        HeatingController controller = new HeatingController();

        boolean result = controller.shouldHeat(
                19000L,
                21500L,
                1200L,
                3700L,
                true
        );

        assertFalse(result,
                "No debe calefaccionar durante el horario punta");
    }

    @Test
    void doesNotHeatWhenAvailablePowerIsInsufficient() {
        HeatingController controller = new HeatingController();

        boolean result = controller.shouldHeat(
                19000L,
                21500L,
                1200L,
                1000L,
                false
        );

        assertFalse(result,
                "No debe calefaccionar si necesita 1.2 kW y solo hay 1.0 kW disponible");
    }

    @Test
    void doesNotHeatWhenTargetTemperatureIsReached() {
        HeatingController controller = new HeatingController();

        boolean result = controller.shouldHeat(
                21500L, 21500L, 1200L, 3700L, false
        );

        assertFalse(result,
                "No debe calefaccionar si ya alcanzó la temperatura objetivo");
    }

    @Test
    void heatsWhenAvailablePowerExactlyMatchesRequiredPower() {
        HeatingController controller = new HeatingController();

        boolean result = controller.shouldHeat(
                19000L, 21500L, 1200L, 1200L, false
        );

        assertTrue(result,
                "Puede calefaccionar si la potencia disponible alcanza exactamente");
    }

    @Test
    void prioritizesColdestRoomWhenPowerCannotSupplyBoth() {
        HeatingController controller = new HeatingController();

        List<RoomState> rooms = List.of(
                new RoomState("room1", 20000L, 21500L, 1200L),
                new RoomState("room2", 18000L, 21500L, 1200L)
        );

        Set<String> selected = controller.selectRoomsToHeat(
                rooms, 2000L, false
        );

        assertEquals(Set.of("room2"), selected,
                "Debe elegir la habitación con mayor déficit sin superar los 2 kW");
    }

    @Test
    void selectsBothRoomsWhenPowerIsSufficient() {
        HeatingController controller = new HeatingController();

        List<RoomState> rooms = List.of(
                new RoomState("room1", 20000L, 21500L, 1200L),
                new RoomState("room2", 18000L, 21500L, 1200L)
        );

        assertEquals(
                Set.of("room1", "room2"),
                controller.selectRoomsToHeat(rooms, 3000L, false)
        );
    }

    @Test
    void selectsNoRoomsDuringPeakHours() {
        HeatingController controller = new HeatingController();

        List<RoomState> rooms = List.of(
                new RoomState("room1", 19000L, 21500L, 1200L),
                new RoomState("room2", 18000L, 21500L, 1200L)
        );

        assertEquals(
                Set.of(),
                controller.selectRoomsToHeat(rooms, 3000L, true)
        );
    }

    @Test
    void breaksEqualDeficitTieByIdRegardlessOfInputOrder() {
        HeatingController controller = new HeatingController();

        RoomState room1 = new RoomState("room1", 19000L, 21500L, 1200L);
        RoomState room2 = new RoomState("room2", 19000L, 21500L, 1200L);

        assertEquals(
                Set.of("room1"),
                controller.selectRoomsToHeat(
                        List.of(room2, room1), 2000L, false)
        );

        assertEquals(
                Set.of("room1"),
                controller.selectRoomsToHeat(
                        List.of(room1, room2), 2000L, false)
        );
    }

    @Test
    void stopsHeatingAtPeakStartAndResumesAtPeakEnd() {
        HeatingController controller = new HeatingController();

        PeakSchedule schedule = new PeakSchedule(
                LocalTime.of(17, 0),
                LocalTime.of(23, 0),
                PeakSchedule.Days.HABILES
        );

        List<RoomState> rooms = List.of(
                new RoomState("room1", 19000L, 21500L, 1200L)
        );

        LocalDateTime beforePeak =
                LocalDateTime.of(2026, 10, 5, 16, 59);
        LocalDateTime peakStart =
                LocalDateTime.of(2026, 10, 5, 17, 0);
        LocalDateTime peakEnd =
                LocalDateTime.of(2026, 10, 5, 23, 0);

        assertEquals(
                Set.of("room1"),
                controller.selectRoomsToHeat(
                        rooms, 3700L, schedule.isPeak(beforePeak))
        );

        assertEquals(
                Set.of(),
                controller.selectRoomsToHeat(
                        rooms, 3700L, schedule.isPeak(peakStart))
        );

        assertEquals(
                Set.of("room1"),
                controller.selectRoomsToHeat(
                        rooms, 3700L, schedule.isPeak(peakEnd))
        );
    }

    @Test
    void selectsNothingWhenThereAreNoRooms() {
        HeatingController controller = new HeatingController();

        assertEquals(
                Set.of(),
                controller.selectRoomsToHeat(List.of(), 3700L, false)
        );
    }

    @Test
    void neverSelectsARoomWhoseOwnPowerExceedsTheSiteMaximum() {
        HeatingController controller = new HeatingController();

        List<RoomState> rooms = List.of(
                new RoomState("room1", 15000L, 21500L, 5000L),
                new RoomState("room2", 19000L, 21500L, 1200L)
        );

        // room1 tiene más déficit pero no cabe: se calefacciona room2.
        assertEquals(
                Set.of("room2"),
                controller.selectRoomsToHeat(rooms, 3700L, false)
        );
    }
}
