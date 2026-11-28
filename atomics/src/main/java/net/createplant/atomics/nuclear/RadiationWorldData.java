package net.createplant.atomics.nuclear;

import net.createplant.block.Finite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RadiationWorldData extends SavedData {
    public final Set<BlockPos> blocks = new HashSet<>();
    public final Map<BlockPos, Radionuclides> blockActivity = new HashMap<>();
    public final Map<Long, Map<BlockPos, Radionuclides>> soil = new HashMap<>();
    public final Map<BlockPos, CoreHistory> cores = new HashMap<>();

    public record CoreHistory(double watts, long tick) {}

    public static RadiationWorldData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new Factory<>(RadiationWorldData::new, RadiationWorldData::load),
                        "createplant_radiation");
    }

    public static RadiationWorldData load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data = new RadiationWorldData();
        for (long packed : tag.getLongArray("Blocks")) data.blocks.add(BlockPos.of(packed));
        for (var raw : tag.getList("BlockActivity", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            data.blockActivity.put(BlockPos.of(row.getLong("Pos")), read(row));
        }
        for (var raw : tag.getList("Soil", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            var pos = BlockPos.of(row.getLong("Pos"));
            var activity = read(row);
            if (activity.total() > 0)
                data.soil
                        .computeIfAbsent(
                                net.minecraft.world.level.ChunkPos.asLong(pos),
                                k -> new HashMap<>())
                        .put(pos, activity);
        }
        for (var raw : tag.getList("Cores", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            data.cores.put(
                    BlockPos.of(row.getLong("Pos")),
                    new CoreHistory(
                            Finite.of(row.getDouble("Watts"), 0, 0, 1e15),
                            Math.max(0, row.getLong("Tick"))));
        }
        return data;
    }

    public static Radionuclides read(CompoundTag tag) {
        return new Radionuclides(
                tag.getDouble("Short"),
                tag.getDouble("Cesium"),
                tag.getDouble("Long"),
                tag.getLong("Tick"));
    }

    public static CompoundTag write(Radionuclides activity) {
        var tag = new CompoundTag();
        tag.putDouble("Short", activity.shortLived());
        tag.putDouble("Cesium", activity.cesium());
        tag.putDouble("Long", activity.longLived());
        tag.putLong("Tick", activity.tick());
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLongArray("Blocks", blocks.stream().mapToLong(BlockPos::asLong).toArray());
        var materials = new ListTag();
        for (var e : blockActivity.entrySet()) {
            var row = write(e.getValue());
            row.putLong("Pos", e.getKey().asLong());
            materials.add(row);
        }
        tag.put("BlockActivity", materials);
        var deposits = new ListTag();
        for (var chunk : soil.values())
            for (var e : chunk.entrySet()) {
                var row = write(e.getValue());
                row.putLong("Pos", e.getKey().asLong());
                deposits.add(row);
            }
        tag.put("Soil", deposits);
        var history = new ListTag();
        for (var e : cores.entrySet()) {
            var row = new CompoundTag();
            row.putLong("Pos", e.getKey().asLong());
            row.putDouble("Watts", e.getValue().watts());
            row.putLong("Tick", e.getValue().tick());
            history.add(row);
        }
        tag.put("Cores", history);
        return tag;
    }
}
