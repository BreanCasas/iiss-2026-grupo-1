package com.ioteste.engine.site;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiteService {

    public record Snapshot(
            SiteInventory inventory,
            List<SiteInventory.Room> pendingShutdowns,
            long revision) {}

    private final SiteRepository repository;
    private final SiteValidator validator = new SiteValidator();

    private SiteInventory active;
    private List<SiteInventory.Room> pending = List.of();
    private long revision;

    public SiteService(SiteRepository repository) {
        this.repository = repository;

        repository.load().ifPresent(inventory -> {
            validator.validate(inventory);
            active = inventory;
            pending = List.copyOf(repository.loadPendingShutdowns());
        });
    }

    public synchronized SiteInventory get() {
        if (active == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Todavía no se cargó un sitio"
            );
        }

        return active;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(get(), pending, revision);
    }

    public synchronized boolean isCurrent(long expectedRevision) {
        return revision == expectedRevision;
    }

    public synchronized SiteInventory replace(SiteInventory inventory) {
        try {
            validator.validate(inventory);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }

        if (inventory.equals(active)) {
            return active;
        }

        List<SiteInventory.Room> nextPending =
                new ArrayList<>(pending);

        if (active != null) {
            // Mantener las direcciones de los switches anteriores,
            // incluso si no aparecen en el inventario nuevo.
            nextPending.addAll(active.habitaciones());
        }

        List<SiteInventory.Room> savedPending =
                List.copyOf(nextPending);

        // Guardar la configuración y los apagados pendientes
        // en el mismo documento antes de modificar la memoria.
        repository.save(inventory, savedPending);

        active = inventory;
        pending = savedPending;
        revision++;

        return active;
    }

    public synchronized boolean confirmShutdowns(long expectedRevision) {
        if (revision != expectedRevision) {
            return false;
        }

        if (!pending.isEmpty()) {
            repository.save(active, List.of());
            pending = List.of();
        }

        return true;
    }
}