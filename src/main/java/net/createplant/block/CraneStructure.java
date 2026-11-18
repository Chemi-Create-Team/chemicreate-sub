package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * A gantry crane: two parallel runway rails of horizontal Create metal girders, each end standing on a leg of upright girders that
 * reaches the ground, or resting directly on a solid block. The crane drive sits in line with one rail, just past its end. The bridge, its
 * trolley and the hoist run along the rails and are drawn, not placed.
 */
public final class CraneStructure {
    public static final int MIN_LENGTH = 5, MAX_LENGTH = 64, MIN_SPAN = 4, MAX_SPAN = 65, MAX_LEGS = 64;

    /**
     * A formed crane: the first block of the driven rail, the direction of the rails and the direction across to the other
     * rail, the rail length, the distance between the rails and the height of the tallest leg under the rails.
     */
    public record Result(BlockPos start, Direction along, Direction across, int length, int span, int legs, BlockPos fault, String reason) {
        public boolean formed() { return along != null; }

        public BlockPos at(double u, double v) { return start.relative(along, (int) Math.round(u)).relative(across, (int) Math.round(v)); }
    }

    private CraneStructure() { }

    private static Result fault(BlockPos pos, String reason) { return new Result(null, null, null, 0, 0, 0, pos, reason); }

    private static boolean rail(Level level, BlockPos pos, Direction.Axis axis) {
        return level.isLoaded(pos) && CraneFrame.rail(level.getBlockState(pos), axis);
    }

    public static Result scan(Level level, BlockPos drive, Direction.Axis axis) {
        if (axis.isVertical()) return fault(drive, "rails");
        for (Direction along : Direction.values()) {
            if (along.getAxis() != axis) continue;
            BlockPos start = drive.relative(along);
            if (!rail(level, start, axis)) continue;
            int length = 1;
            while (length <= MAX_LENGTH && rail(level, start.relative(along, length), axis)) length++;
            if (!level.isLoaded(start.relative(along, length))) return fault(start.relative(along, length), "unloaded");
            if (length < MIN_LENGTH || length > MAX_LENGTH) return fault(start, "length");
            for (Direction across : Direction.Plane.HORIZONTAL) {
                if (across.getAxis() == axis) continue;
                for (int span = MIN_SPAN; span <= MAX_SPAN; span++) {
                    BlockPos other = start.relative(across, span);
                    if (!rail(level, other, axis)) continue;
                    for (int u = 0; u < length; u++) if (!rail(level, other.relative(along, u), axis))
                        return fault(other.relative(along, u), level.isLoaded(other.relative(along, u)) ? "rails" : "unloaded");
                    if (rail(level, other.relative(along, length), axis) || rail(level, other.relative(along, -1), axis)) return fault(other, "rails");
                    return checkSupports(level, drive, start, along, across, length, span);
                }
            }
            return fault(start, "rails");
        }
        return fault(drive, "rails");
    }

    public static Result scanConfigured(Level level, BlockPos drive, Direction along, Direction across, int length, int span) {
        Direction.Axis axis = along.getAxis();
        if (axis.isVertical() || across.getAxis().isVertical() || across.getAxis() == axis) return fault(drive, "rails");
        if (length < MIN_LENGTH || length > MAX_LENGTH || span < MIN_SPAN || span > MAX_SPAN) return fault(drive, "frame_dimensions");
        BlockPos start = drive.relative(along), other = start.relative(across, span);
        for (BlockPos first : new BlockPos[] {start, other}) {
            for (int u = -1; u <= length; u++) {
                BlockPos at = first.relative(along, u);
                if (!level.isLoaded(at)) return fault(at, "unloaded");
                boolean girder = CraneFrame.rail(level.getBlockState(at), axis);
                if (u < 0 || u == length) {
                    if (girder) return fault(at, "frame_dimensions");
                } else if (!girder) return fault(at, "rails");
            }
        }
        return checkSupports(level, drive, start, along, across, length, span);
    }

    private static Result checkSupports(Level level, BlockPos drive, BlockPos start, Direction along, Direction across, int length, int span) {
        BlockPos other = start.relative(across, span);
        int legs = 0;
        for (BlockPos corner : new BlockPos[] {start, start.relative(along, length - 1), other, other.relative(along, length - 1)}) {
            int height = 0;
            while (height <= MAX_LEGS && level.isLoaded(corner.below(height + 1)) && CraneFrame.leg(level.getBlockState(corner.below(height + 1)))) height++;
            BlockPos ground = corner.below(height + 1);
            if (height > MAX_LEGS || !level.isLoaded(ground) || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) return fault(ground, "legs");
            legs = Math.max(legs, height);
        }
        return new Result(start, along, across, length, span, legs, drive, "ready");
    }
}
