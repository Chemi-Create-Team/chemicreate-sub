package net.createplant.nuclear;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class FuelPoolStructure {
    /** Twenty-one-metre assemblies need one metre of water above their lifted ends. */
    public static final int MIN = 2, MAX = 9, DEPTH = ReactorPhysics.FUEL_ASSEMBLY_LENGTH + 1;
    private static final Map<Level, Map<BlockPos, Result>> CACHE = new WeakHashMap<>();

    public record Result(List<BlockPos> cells, String status, BlockPos fault, long checked) {
        public boolean formed() { return status.equals("ready"); }
    }

    private FuelPoolStructure() { }

    public static void invalidate(Level level) {
        var previous = CACHE.remove(level);
        if (previous != null && !level.isClientSide()) for (BlockPos pos : previous.keySet())
            if (level.hasChunkAt(pos)) level.invalidateCapabilities(pos);
    }

    public static void changed(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        var cache = CACHE.get(level);
        if (cache == null) return;
        BlockPos pos = event.getPos();
        for (BlockPos rack : cache.keySet()) {
            if (pos.getY() >= rack.getY() && pos.getY() <= rack.getY() + DEPTH
                && Math.abs(pos.getX() - rack.getX()) <= MAX && Math.abs(pos.getZ() - rack.getZ()) <= MAX) {
                invalidate(level);
                return;
            }
        }
    }

    public static Result get(Level level, BlockPos start) {
        var cache = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        Result result = cache.get(start);
        if (result != null && level.getGameTime() - result.checked() < 20
            && result.cells().stream().allMatch(p -> level.hasChunkAt(p)
                && level.hasChunkAt(p.north()) && level.hasChunkAt(p.south()) && level.hasChunkAt(p.east()) && level.hasChunkAt(p.west()))) return result;
        Result previous = result;
        result = scan(level, start);
        if (result.formed()) {
            for (BlockPos cell : result.cells()) cache.put(cell, result);
            if (!level.isClientSide() && (previous == null || !previous.formed() || !previous.cells().equals(result.cells())))
                for (BlockPos cell : result.cells()) level.invalidateCapabilities(cell);
        }
        else cache.put(start, result);
        return result;
    }

    private static Result scan(Level level, BlockPos start) {
        var pending = new ArrayDeque<BlockPos>();
        var cells = new HashSet<BlockPos>();
        pending.add(start);
        int minX = start.getX(), maxX = minX, minZ = start.getZ(), maxZ = minZ;
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!level.hasChunkAt(pos)) return failure(level, cells, "unloaded", pos);
            if (!level.getBlockState(pos).is(CENuclear.FUEL_POOL.get()) || !cells.add(pos)) continue;
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
            if (maxX - minX >= MAX || maxZ - minZ >= MAX) return failure(level, cells, "size", pos);
            for (Direction side : Direction.Plane.HORIZONTAL) pending.add(pos.relative(side));
        }
        int width = maxX - minX + 1, length = maxZ - minZ + 1;
        if (width < MIN || length < MIN || cells.size() != width * length) return failure(level, cells, "size", start);
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = minX - 1; x <= maxX + 1; x++) for (int z = minZ - 1; z <= maxZ + 1; z++) {
            boolean wall = x < minX || x > maxX || z < minZ || z > maxZ;
            for (int y = start.getY(); y <= start.getY() + DEPTH; y++) {
                cursor.set(x, y, z);
                if (!level.hasChunkAt(cursor)) return failure(level, cells, "unloaded", cursor);
                var state = level.getBlockState(cursor);
                if (wall ? !state.is(CEMetals.STEEL_BLOCK.get().getBlock()) : y > start.getY() && !state.isAir())
                    return failure(level, cells, wall ? "wall" : "blocked", cursor);
            }
        }
        return new Result(cells.stream().sorted().toList(), "ready", start, level.getGameTime());
    }

    private static Result failure(Level level, java.util.Collection<BlockPos> cells, String status, BlockPos fault) {
        return new Result(List.copyOf(cells), status, fault.immutable(), level.getGameTime());
    }
}
