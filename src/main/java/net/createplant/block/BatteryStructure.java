package net.createplant.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * A battery bank is a solid box of one chemistry's casings with its terminal block on the top face. Searches never load chunks.
 * Returns the number of casings, or a negative reason: -1 no casing under the terminal, -2 not a solid box, -3 too small or too large, -4 unloaded.
 */
public final class BatteryStructure {
    public static final int MIN_SIDE = 2, MAX_CASINGS = 4096;
    public static final int NO_CASING = -1, NOT_A_BOX = -2, SIZE = -3, UNLOADED = -4;

    private BatteryStructure() { }

    public static int find(Level level, BlockPos terminal, BatteryChemistry chemistry) {
        var start = terminal.below();
        if (!level.hasChunkAt(start)) return UNLOADED;
        var casing = chemistry.casing();
        if (!level.getBlockState(start).is(casing)) return NO_CASING;
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(start);queue.add(start);
        int minX = start.getX(), maxX = minX, minY = start.getY(), maxY = minY, minZ = start.getZ(), maxZ = minZ;
        while (!queue.isEmpty()) {
            var at = queue.poll();
            minX = Math.min(minX, at.getX());maxX = Math.max(maxX, at.getX());
            minY = Math.min(minY, at.getY());maxY = Math.max(maxY, at.getY());
            minZ = Math.min(minZ, at.getZ());maxZ = Math.max(maxZ, at.getZ());
            for (var side : Direction.values()) {
                var next = at.relative(side);
                if (seen.contains(next)) continue;
                if (!level.hasChunkAt(next)) return UNLOADED;
                if (!level.getBlockState(next).is(casing)) continue;
                if (seen.size() >= MAX_CASINGS) return SIZE;
                seen.add(next);queue.add(next);
            }
        }
        int dx = maxX - minX + 1, dy = maxY - minY + 1, dz = maxZ - minZ + 1;
        if (seen.size() != dx * dy * dz) return NOT_A_BOX;
        if (dx < MIN_SIDE || dy < MIN_SIDE || dz < MIN_SIDE) return SIZE;
        boolean onTop = terminal.getY() == maxY + 1 && terminal.getX() >= minX && terminal.getX() <= maxX && terminal.getZ() >= minZ && terminal.getZ() <= maxZ;
        return onTop ? seen.size() : NOT_A_BOX;
    }

    public static String reason(int result) {
        return switch (result) { case NO_CASING -> "casing"; case NOT_A_BOX -> "box"; case SIZE -> "size"; case UNLOADED -> "unloaded"; default -> "ok"; };
    }
}
