package com.ioteste.engine.site;

import java.util.List;
import java.util.Optional;

public interface SiteRepository {

    Optional<SiteInventory> load();

    List<SiteInventory.Room> loadPendingShutdowns();

    void save(
            SiteInventory inventory,
            List<SiteInventory.Room> pendingShutdowns);
}