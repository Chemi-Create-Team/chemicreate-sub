package net.createplant.event;

import java.util.*;
import net.createplant.CreatePlant;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Watch interior air as well as the shell, without rescanning formed tanks every tick. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class WetProcessChanges {
    private static final class Index {
        final Map<BlockPos, Set<BlockPos>> byCell = new HashMap<>(), byController = new HashMap<>();
    }
    private static final Map<Level, Index> WATCHES = new WeakHashMap<>();
    private WetProcessChanges() { }
    public static void watch(Level level, BlockPos drive, Set<BlockPos> cells) {
        forget(level, drive);
        var index = WATCHES.computeIfAbsent(level, unused -> new Index());
        index.byController.put(drive.immutable(), Set.copyOf(cells));
        for (var cell : cells) index.byCell.computeIfAbsent(cell, unused -> new HashSet<>()).add(drive.immutable());
    }
    public static void forget(Level level, BlockPos drive) {
        var index = WATCHES.get(level);if (index == null) return;
        var cells = index.byController.remove(drive);if (cells == null) return;
        for (var cell : cells) { var roots = index.byCell.get(cell);roots.remove(drive);if (roots.isEmpty()) index.byCell.remove(cell); }
        if (index.byController.isEmpty()) WATCHES.remove(level);
    }
    @SubscribeEvent public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        var index = WATCHES.get(level);if (index == null) return;
        var roots = index.byCell.get(event.getPos());if (roots == null) return;
        for (var root : roots) if (level.hasChunkAt(root) && level.getBlockEntity(root) instanceof WetProcessBlockEntity be) be.cellChanged(event.getPos());
    }
}
