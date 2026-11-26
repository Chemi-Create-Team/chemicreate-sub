package net.createplant.factory;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

/** A block edit wakes only controllers whose clearance or frame might include the edited section. */
public final class FactoryWatch {
    private static final WeakHashMap<ServerLevel, Long2ObjectOpenHashMap<Set<BlockPos>>> WATCHED = new WeakHashMap<>();

    private FactoryWatch() { }

    public static void update(ServerLevel level, BlockPos controller, Direction facing, boolean add) {
        var sections = add ? WATCHED.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>()) : WATCHED.get(level);
        if (sections == null) return;
        var layout = new FactoryLayout(controller, facing, FactoryLayout.MAX_LENGTH, java.util.List.of());
        var start = layout.at(-3, -1, 0);
        var end = layout.at(3, 6, FactoryLayout.MAX_LENGTH);
        for (int x = section(Math.min(start.getX(), end.getX())); x <= section(Math.max(start.getX(), end.getX())); x++)
            for (int y = section(start.getY()); y <= section(end.getY()); y++)
                for (int z = section(Math.min(start.getZ(), end.getZ())); z <= section(Math.max(start.getZ(), end.getZ())); z++) {
                    long key = SectionPos.asLong(x, y, z);
                    if (add) sections.computeIfAbsent(key, ignored -> new HashSet<>()).add(controller);
                    else {
                        var entries = sections.get(key);
                        if (entries != null && entries.remove(controller) && entries.isEmpty()) sections.remove(key);
                    }
                }
        if (sections.isEmpty()) WATCHED.remove(level);
    }

    private static int section(int coord) { return SectionPos.blockToSectionCoord(coord); }

    public static void changed(ServerLevel level, BlockPos pos) {
        var sections = WATCHED.get(level);
        if (sections == null) return;
        var owners = sections.get(SectionPos.asLong(section(pos.getX()), section(pos.getY()), section(pos.getZ())));
        if (owners == null) return;
        for (var owner : owners) if (level.hasChunkAt(owner) && level.getBlockEntity(owner) instanceof ContinuousFactoryBlockEntity factory)
            factory.structureChanged();
    }
}
