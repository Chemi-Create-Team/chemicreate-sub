package net.createplant.atomics.nuclear;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.createplant.atomics.block.FuelChannelBlock;
import net.createplant.atomics.block.GraphiteModeratorBlock;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A core of vertical columns three or seven blocks high, each either a four-position channel bundle or plain graphite. Circular cores
 * are recommended in five, seven, nine, eleven and thirteen-block diameters; the two-block biological shield makes their outside
 * diameters nine through seventeen metres. Nothing of the core may touch its outside, so two cores never merge and a stray block
 * is reported.
 */
public final class ReactorCoreStructure {
    public static final int MIN_WIDTH = ReactorCoreVariants.MIN_WIDTH, MAX_WIDTH = ReactorCoreVariants.MAX_WIDTH;
    public static final int SHIELD_THICKNESS = ReactorCoreVariants.SHIELD_THICKNESS;
    public static final int OUTER_DIAMETER = MAX_WIDTH + 2 * SHIELD_THICKNESS, HEIGHT = ReactorPhysics.CORE_HEIGHT;
    public static final int MAX_CHANNELS = 448, DEFAULT_CONTROL_CHANNELS = 51, DEFAULT_FUEL_CHANNELS = 397;
    public static final int FULL_CHANNEL_MASK = (1 << ReactorPhysics.RODS_PER_BLOCK) - 1;

    public enum Kind { GRAPHITE, FUEL }

    /** A formed core: its box, whether its footprint is a circle, the bottom cell of each column of each kind, and the controller. */
    public record Result(BoundingBox box, boolean circular, List<BlockPos> fuel, List<BlockPos> graphite,
                         BlockPos controller, BlockPos fault, String reason) {
        public boolean formed() { return controller != null; }
        public int width() { return box.getXSpan(); }
        public int depth() { return box.getZSpan(); }
        public int height() { return box.getYSpan(); }
    }

    private ReactorCoreStructure() { }

    public static Kind kind(Block block) {
        if (block instanceof FuelChannelBlock) return Kind.FUEL;
        if (block instanceof GraphiteModeratorBlock) return Kind.GRAPHITE;
        return null;
    }

    /** Whether column (i, j) of a square footprint {@code size} wide lies in its inscribed circle. */
    public static boolean inCircle(int i, int j, int size) { return ReactorCoreVariants.inCircle(i, j, size); }

    private static Kind kind(Level level, BlockPos pos) { return kind(level.getBlockState(pos).getBlock()); }

    private static Result fault(BlockPos pos, String reason) {
        return new Result(null, false, List.of(), List.of(), null, pos, reason);
    }

    /** Active pressure-channel positions of one bundle, clipped to the circular fine-channel edge. */
    public static int channelMask(Result result, BlockPos bottom) {
        if (result == null || !result.circular()) return FULL_CHANNEL_MASK;
        int bx = bottom.getX() - result.box().minX(), bz = bottom.getZ() - result.box().minZ(), size = result.width(), mask = 0;
        for (int slot = 0; slot < ReactorPhysics.RODS_PER_BLOCK; slot++) {
            int sx = slot % ReactorPhysics.CHANNEL_GRID, sz = slot / ReactorPhysics.CHANNEL_GRID;
            if (ReactorCoreVariants.activeChannel(bx, bz, sx, sz, size)) mask |= 1 << slot;
        }
        return mask;
    }

    /** The default control-channel positions among a bundle's active slots. */
    public static int defaultControlMask(Result result, BlockPos bottom) {
        return controlMask(result, bottom, result.circular(), false);
    }

    static int legacyControlMask(Result result, BlockPos bottom, boolean previousMinimum) {
        return controlMask(result, bottom, false, previousMinimum);
    }

    private static int controlMask(Result result, BlockPos bottom, boolean circularPreset, boolean previousMinimum) {
        int active = channelMask(result, bottom), mask = 0;
        int bx = bottom.getX() - result.box().minX(), bz = bottom.getZ() - result.box().minZ();
        for (int slot = 0; slot < ReactorPhysics.RODS_PER_BLOCK; slot++) {
            if ((active & 1 << slot) == 0) continue;
            boolean control = previousMinimum ? ReactorCoreVariants.previousMinimumControlChannel(bx, bz, slot)
                : ReactorCoreVariants.defaultControlChannel(bx, bz, slot, result.width(), circularPreset);
            if (control) mask |= 1 << slot;
        }
        return mask;
    }

    public static Result scan(Level level, BlockPos start) {
        if (!level.isLoaded(start) || kind(level, start) == null) return fault(start, "structure");
        int y0 = start.getY(), y1 = start.getY();
        while (y1 - y0 < HEIGHT && loadedCore(level, start.getX(), y0 - 1, start.getZ())) y0--;
        while (y1 - y0 < HEIGHT && loadedCore(level, start.getX(), y1 + 1, start.getZ())) y1++;
        if (!ReactorPhysics.supportedHeight(y1 - y0 + 1)) return fault(start.atY(y0), "height");
        // The footprint is the bottom layer connected to the start; it may not spread beyond the largest core.
        var footprint = new HashSet<Long>();
        var queue = new ArrayDeque<BlockPos>();
        BlockPos first = start.atY(y0);
        footprint.add(first.asLong());
        queue.add(first);
        int x0 = first.getX(), x1 = x0, z0 = first.getZ(), z1 = z0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (BlockPos next : List.of(pos.east(), pos.west(), pos.south(), pos.north())) {
                if (footprint.contains(next.asLong()) || !loadedCore(level, next.getX(), y0, next.getZ())) continue;
                x0 = Math.min(x0, next.getX());
                x1 = Math.max(x1, next.getX());
                z0 = Math.min(z0, next.getZ());
                z1 = Math.max(z1, next.getZ());
                if (x1 - x0 >= MAX_WIDTH || z1 - z0 >= MAX_WIDTH) return fault(next, "size");
                footprint.add(next.asLong());
                queue.add(next);
            }
        }
        int width = x1 - x0 + 1, depth = z1 - z0 + 1;
        BlockPos corner = new BlockPos(x0, y0, z0);
        if (width < MIN_WIDTH || depth < MIN_WIDTH) return fault(corner, "size");
        boolean circular = footprint.size() != width * depth;
        if (circular && width != depth) return fault(corner, "shape");
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) for (int y = y0 - 1; y <= y1 + 1; y++)
            if (!level.isLoaded(new BlockPos(x, y, z))) return fault(new BlockPos(x, y, z), "unloaded");
        List<BlockPos> fuel = new ArrayList<>(), graphite = new ArrayList<>();
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean inBox = x >= x0 && x <= x1 && z >= z0 && z <= z1;
            boolean inside = inBox && (!circular || inCircle(x - x0, z - z0, width));
            if (inBox && inside != footprint.contains(BlockPos.asLong(x, y0, z))) return fault(new BlockPos(x, y0, z), "shape");
            Kind column = inside ? kind(level, cursor.set(x, y0, z)) : null;
            for (int y = y0 - 1; y <= y1 + 1; y++) {
                Kind here = kind(level, cursor.set(x, y, z));
                boolean core = inside && y >= y0 && y <= y1;
                if (core ? here != column : here != null) return fault(cursor.immutable(), core ? "column" : "outside");
                if (core && here == Kind.FUEL && level.getBlockEntity(cursor) instanceof FuelChannelBlockEntity channel
                    && (y == y0 ? !channel.acceptsAssemblyHeight(y1 - y0 + 1) : channel.hasStoredColumnState()))
                    return fault(cursor.immutable(), "fuel_height");
            }
            if (column == Kind.FUEL) fuel.add(new BlockPos(x, y0, z));
            if (column == Kind.GRAPHITE) graphite.add(new BlockPos(x, y0, z));
        }
        if (fuel.isEmpty()) return fault(corner, "fuel");
        return new Result(new BoundingBox(x0, y0, z0, x1, y1, z1), circular, List.copyOf(fuel), List.copyOf(graphite),
            fuel.getFirst(), corner, "ready");
    }

    private static boolean loadedCore(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) && kind(level, pos) != null;
    }
}
