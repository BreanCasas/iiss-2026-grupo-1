package com.ioteste.engine.site;

import com.ioteste.core.PeakSchedule;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SiteValidatorTest {

    private final SiteValidator validator = new SiteValidator();

    private SiteInventory.Site site() {
        return new SiteInventory.Site(
                "casa",
                "Casa",
                3.7,
                new SiteInventory.Tariff(
                        new SiteInventory.PeakPeriod(
                                "17:00",
                                "23:00",
                                PeakSchedule.Days.HABILES
                        )
                )
        );
    }

    private SiteInventory.Room room(
            String id,
            String thermostatId,
            String switchId) {

        return new SiteInventory.Room(
                id,
                "Living",
                21.5,
                1.2,
                thermostatId,
                thermostatId + "/status/temperature:0",
                switchId,
                "http://localhost:9090"
        );
    }

    @Test
    void rejectsDuplicateRoomIds() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(
                        room("room1", "ht-room1", "sw-room1"),
                        room("room1", "ht-room2", "sw-room2")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsDuplicateThermostatIds() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(
                        room("room1", "ht-room1", "sw-room1"),
                        room("room2", "ht-room1", "sw-room2")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsDuplicateSwitchIds() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(
                        room("room1", "ht-room1", "sw-room1"),
                        room("room2", "ht-room2", "sw-room1")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void acceptsValidInventoryEvenWhenTotalPowerExceedsContractedPower() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(
                        room("room1", "ht-room1", "sw-room1"),
                        room("room2", "ht-room2", "sw-room2"),
                        room("room3", "ht-room3", "sw-room3"),
                        room("room4", "ht-room4", "sw-room4")
                )
        );

        assertDoesNotThrow(() -> validator.validate(inventory));
    }

    @Test
    void rejectsUnsupportedMajorVersion() {
        SiteInventory inventory = new SiteInventory(
                "3.0",
                site(),
                List.of(room("room1", "ht-room1", "sw-room1"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsZeroContractedPower() {
        SiteInventory.Site invalidSite = new SiteInventory.Site(
                "casa",
                "Casa",
                0.0,
                site().tarifa()
        );

        SiteInventory inventory = new SiteInventory(
                "2.0",
                invalidSite,
                List.of(room("room1", "ht-room1", "sw-room1"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsNegativeRoomPower() {
        SiteInventory.Room invalidRoom = new SiteInventory.Room(
                "room1",
                "Living",
                21.5,
                -1.2,
                "ht-room1",
                "ht-room1/status/temperature:0",
                "sw-room1",
                "http://localhost:9090"
        );

        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(invalidRoom)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    private SiteInventory inventoryWithPeakPeriod(
            String from,
            String until) {

        SiteInventory.Site configuredSite = new SiteInventory.Site(
                "casa",
                "Casa",
                3.7,
                new SiteInventory.Tariff(
                        new SiteInventory.PeakPeriod(
                                from,
                                until,
                                PeakSchedule.Days.HABILES
                        )
                )
        );

        return new SiteInventory(
                "2.0",
                configuredSite,
                List.of(room("room1", "ht-room1", "sw-room1"))
        );
    }

    @Test
    void rejectsPeakPeriodWithEqualStartAndEnd() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        inventoryWithPeakPeriod("17:00", "17:00"))
        );
    }

    @Test
    void rejectsPeakPeriodThatCrossesMidnight() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(
                        inventoryWithPeakPeriod("23:00", "06:00"))
        );
    }

    @Test
    void rejectsMissingSite() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                null,
                List.of(room("room1", "ht-room1", "sw-room1"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsMissingRoomsList() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }

    @Test
    void rejectsEmptyRoomId() {
        SiteInventory inventory = new SiteInventory(
                "2.0",
                site(),
                List.of(room("", "ht-room1", "sw-room1"))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(inventory)
        );
    }
}