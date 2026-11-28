package net.createplant.registry;

import net.createplant.block.ProcessPorts;
import net.createplant.block.entity.IndustrialDispatchBlockEntity;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.event.FacilityAchievements;

/** Hooks the logistics addon installs into core extension points. */
public final class LogisticsSetup {
    private LogisticsSetup() { }

    public static void init() {
        ProcessPorts.registerItemPorts(IndustrialDispatchBlockEntity.class);
        FacilityAchievements.register("quarry", QuarryDriveBlockEntity.class, QuarryDriveBlockEntity::built);
    }
}
