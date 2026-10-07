package com.ioteste.engine.site;

import com.ioteste.core.PeakSchedule;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteServiceTest {

    private SiteRepository repository(SiteInventory previous) {
        SiteRepository repository = mock(SiteRepository.class);

        when(repository.load()).thenReturn(Optional.of(previous));
        when(repository.loadPendingShutdowns()).thenReturn(List.of());

        return repository;
    }

    @Test
    void rejectsInvalidInventoryWithoutPersistingIt() {
        SiteRepository repository = repository(inventory(true));
        SiteService service = new SiteService(repository);

        assertThrows(
                ResponseStatusException.class,
                () -> service.replace(
                        new SiteInventory("3.0", null, null)
                )
        );

        verify(repository, never()).save(any(), anyList());
    }

    @Test
    void savesReplacementWithPreviousSwitchesPending() {
        SiteInventory previous = inventory(true);
        SiteInventory replacement = inventory(false);
        SiteRepository repository = repository(previous);
        SiteService service = new SiteService(repository);

        assertSame(replacement, service.replace(replacement));

        verify(repository).save(
                replacement,
                previous.habitaciones()
        );

        assertEquals(
                previous.habitaciones(),
                service.snapshot().pendingShutdowns()
        );
    }

    @Test
    void keepsPreviousInventoryWhenPersistenceFails() {
        SiteInventory previous = inventory(true);
        SiteRepository repository = repository(previous);
        SiteService service = new SiteService(repository);

        doThrow(new IllegalStateException("Mongo inaccesible"))
                .when(repository)
                .save(any(), anyList());

        assertThrows(
                IllegalStateException.class,
                () -> service.replace(inventory(false))
        );

        assertSame(previous, service.get());
        assertTrue(service.snapshot().pendingShutdowns().isEmpty());
    }

    @Test
    void acceptsRepeatedInventoryWithoutSavingAgain() {
        SiteInventory previous = inventory(true);
        SiteRepository repository = repository(previous);
        SiteService service = new SiteService(repository);

        assertSame(previous, service.replace(inventory(true)));

        verify(repository, never()).save(any(), anyList());
    }

    @Test
    void restoresPendingShutdownsAfterRestart() {
        SiteInventory previous = inventory(true);
        SiteRepository repository = repository(inventory(false));

        when(repository.loadPendingShutdowns())
                .thenReturn(previous.habitaciones());

        SiteService service = new SiteService(repository);

        assertEquals(
                previous.habitaciones(),
                service.snapshot().pendingShutdowns()
        );

        assertTrue(service.confirmShutdowns(
                service.snapshot().revision()
        ));

        verify(repository).save(inventory(false), List.of());
    }

    @Test
    void oldCycleCannotClearShutdownsAddedByAnotherReplacement() {
        SiteInventory previous = inventory(true);
        SiteRepository repository = repository(previous);
        SiteService service = new SiteService(repository);

        service.replace(inventory(false));

        long oldRevision = service.snapshot().revision();

        service.replace(previous);

        assertFalse(service.confirmShutdowns(oldRevision));

        assertEquals(
                previous.habitaciones(),
                service.snapshot().pendingShutdowns()
        );
    }

    private SiteInventory inventory(boolean includeRoom) {
        SiteInventory.Site site = new SiteInventory.Site(
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

        SiteInventory.Room room = new SiteInventory.Room(
                "room1",
                "Living",
                21.5,
                1.2,
                "ht-room1",
                "ht-room1/status/temperature:0",
                "sw-room1",
                "http://localhost:9090"
        );

        return new SiteInventory(
                "2.0",
                site,
                includeRoom ? List.of(room) : List.of()
        );
    }
}