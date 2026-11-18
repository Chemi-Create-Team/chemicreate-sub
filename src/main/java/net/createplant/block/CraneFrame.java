package net.createplant.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The frame of a gantry crane that the drive builds from its range settings: a leg of metal girders under each of the four rail
 * ends, reaching down to the ground, then the two runway rails of horizontal girders. Legs come first so that the crane only forms
 * once everything stands. The near rail starts beside the drive; the far rail lies across the width.
 */
public final class CraneFrame {
    private CraneFrame() { }

    /**
     * The frame for a quarry of {@code width} blocks across and {@code length} along the rails, the first rail block at {@code start},
     * with the height of the leg under each corner: near start, near end, far start, far end. A negative height means the ground
     * there is out of reach.
     */
    public record Plan(BlockPos start, Direction along, Direction across, int length, int width, int[] legHeights) {
        public boolean reachable() { return Arrays.stream(legHeights).allMatch(height -> height >= 0); }

        public int legs() { return Arrays.stream(legHeights).sum(); }

        public int rails() { return 2 * length; }

        public int total() { return rails() + legs(); }

        public boolean leg(int n) { return n < legs(); }

        public BlockPos corner(int index) {
            BlockPos rail = index < 2 ? start : start.relative(across, width + 1);
            return index % 2 == 0 ? rail : rail.relative(along, length - 1);
        }

        /** The {@code n}-th block in building order: each corner's leg from the rail down, then the near rail and the far rail. */
        public BlockPos pos(int n) {
            if (n >= legs()) {
                int rail = n - legs();
                return rail < length ? start.relative(along, rail) : start.relative(across, width + 1).relative(along, rail - length);
            }
            for (int index = 0; index < 4; index++) {
                if (n < legHeights[index]) return corner(index).below(n + 1);
                n -= legHeights[index];
            }
            throw new IndexOutOfBoundsException(n);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Plan plan && start.equals(plan.start) && along == plan.along && across == plan.across
                && length == plan.length && width == plan.width && Arrays.equals(legHeights, plan.legHeights);
        }

        @Override public int hashCode() { return start.hashCode() * 31 + length * 7 + width + Arrays.hashCode(legHeights); }
    }

    /** The first part still to build, or -1 when the frame stands, and how many parts are missing. */
    public record Progress(int next, int missing) { }

    public static boolean placed(BlockGetter level, Plan plan, int n) {
        BlockState state = level.getBlockState(plan.pos(n));
        return plan.leg(n) ? leg(state) : rail(state, plan.along().getAxis());
    }

    /** Where building stands, or null while part of the frame is unloaded. */
    public static Progress progress(Level level, Plan plan) {
        int next = -1, missing = 0;
        for (int n = 0; n < plan.total(); n++) {
            if (!level.hasChunkAt(plan.pos(n))) return null;
            if (placed(level, plan, n)) continue;
            if (next < 0) next = n;
            missing++;
        }
        return new Progress(next, missing);
    }

    /** Sets the {@code n}-th part, taken from the drive's store by the caller. */
    public static void place(Level level, Plan plan, int n) {
        BlockPos pos = plan.pos(n);
        level.setBlock(pos, girder(level, pos, plan.leg(n) ? Direction.Axis.Y : plan.along().getAxis()), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS, .8f, 1f);
    }

    /** The frame as it would stand here, each leg reaching down to the first solid block under its corner. */
    public static Plan plan(BlockGetter level, BlockPos start, Direction along, Direction across, int length, int width) {
        int[] heights = new int[4];
        Plan shape = new Plan(start, along, across, length, width, heights);
        for (int index = 0; index < 4; index++) heights[index] = legHeight(level, shape.corner(index));
        return shape;
    }

    /** Girders or open space count as leg; the first other block is the ground. */
    private static int legHeight(BlockGetter level, BlockPos corner) {
        for (int height = 0; height <= CraneStructure.MAX_LEGS; height++) {
            BlockState below = level.getBlockState(corner.below(height + 1));
            if (!leg(below) && !below.canBeReplaced()) return height;
        }
        return -1;
    }

    public static boolean leg(BlockState state) { return AllBlocks.METAL_GIRDER.has(state) && state.getValue(GirderBlock.AXIS) == Direction.Axis.Y; }

    public static boolean rail(BlockState state, Direction.Axis axis) { return AllBlocks.METAL_GIRDER.has(state) && state.getValue(GirderBlock.AXIS) == axis; }

    /** A metal girder lying along {@code axis}, or standing for a leg, joined to the girders already around it. */
    public static BlockState girder(LevelAccessor level, BlockPos pos, Direction.Axis axis) {
        BlockState state = AllBlocks.METAL_GIRDER.getDefaultState().setValue(GirderBlock.AXIS, axis)
            .setValue(GirderBlock.X, axis == Direction.Axis.X).setValue(GirderBlock.Z, axis == Direction.Axis.Z);
        for (Direction side : Direction.values()) state = GirderBlock.updateState(level, pos, state, side);
        return state;
    }
}
