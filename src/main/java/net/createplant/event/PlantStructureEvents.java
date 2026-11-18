package net.createplant.event;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.createplant.block.PlantStructureListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class PlantStructureEvents {
    private static final Map<Level, Set<BlockPos>> OWNERS = new WeakHashMap<>();
    private PlantStructureEvents() { }
    public static void register(Level level, BlockPos pos) { if (level != null && !level.isClientSide()) OWNERS.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable()); }
    public static void remove(Level level, BlockPos pos) { if (OWNERS.containsKey(level)) OWNERS.get(level).remove(pos); }
    public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) return;
        var owners = OWNERS.get(level);if (owners == null) return;
        for (BlockPos owner : owners) if (level.hasChunkAt(owner) && level.getBlockEntity(owner) instanceof PlantStructureListener listener
            && listener.containsStructure(event.getPos())) listener.structureChanged();
    }
    public static void unloaded(LevelEvent.Unload event) { OWNERS.remove(event.getLevel()); }
}
