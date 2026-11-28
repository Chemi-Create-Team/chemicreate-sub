package net.createplant.atomics.factory;

import java.util.ArrayList;
import java.util.List;
import net.createplant.atomics.block.FactoryPortBlock;
import net.createplant.atomics.block.FactoryStageBlock;
import net.createplant.atomics.registry.CEContinuousFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The controller is the first cell of the line. All offsets use +Z through the machine and +X east when the flow faces south. */
public record FactoryLayout(BlockPos origin, Direction forward, int length, List<BlockPos> drives) {
    public static final int MAX_LENGTH = 16;
    public static final int MILL_SPACING = 3;

    public BlockPos at(int x, int y, int z) { return origin.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y); }
    public BlockPos outlet() { return at(0, 0, length); }
    public float yaw() { return (float) Math.toDegrees(Math.atan2(forward.getStepX(), forward.getStepZ())); }
    public Vec3 point(double x, double y, double z) {
        var across = forward.getCounterClockWise();
        return origin.getCenter().add(across.getStepX() * x + forward.getStepX() * z, y,
            across.getStepZ() * x + forward.getStepZ() * z);
    }

    public static FactoryLayout find(Level level, BlockPos origin, Direction facing, FactoryKind kind) {
        if (level.isOutsideBuildHeight(origin.below()) || level.isOutsideBuildHeight(origin.above(6))) return null;
        var lookup = new FactoryLayout(origin, facing, 0, List.of());
        for (int distance = 1; distance <= MAX_LENGTH; distance++) {
            var pos = lookup.at(0, 0, distance);
            if (!level.hasChunkAt(pos)) return null;
            var state = level.getBlockState(pos);
            if (state.is(CEContinuousFactory.OUTLET.get()) && state.getValue(FactoryPortBlock.FACING) == facing)
                return validate(level, new FactoryLayout(origin, facing, distance, List.of()), kind);
            if (kind == FactoryKind.WINDER && distance >= 6) return null;
        }
        return null;
    }

    private static FactoryLayout validate(Level level, FactoryLayout layout, FactoryKind kind) {
        int length = layout.length;
        if (!layout.loaded(level)) return null;
        return switch (kind) {
            case KILN -> length >= 8 && length <= 16 && layout.frame(level, 0, 0, 1)
                && layout.frame(level, 0, 0, length - 2) && layout.frame(level, 0, 0, length / 2)
                && level.getBlockState(layout.outlet().above()).is(CEContinuousFactory.KILN_STACK.get()) ? layout : null;
            case MILL -> layout.mill(level);
            case WINDER -> length == 6 && layout.frame(level, -2, 0, 2) && layout.frame(level, -2, 1, 2)
                && layout.frame(level, 2, 0, 2) && layout.frame(level, 0, 0, 4)
                && layout.stage(level, 2, 1, 2, true) ? new FactoryLayout(layout.origin, layout.forward, length, List.of(layout.at(2, 1, 2))) : null;
        };
    }

    private FactoryLayout mill(Level level) {
        if (length < 7 || (length - 4) % MILL_SPACING != 0) return null;
        var positions = new ArrayList<BlockPos>();
        for (int z = 2; z < length - 1; z += MILL_SPACING) {
            if (!stage(level, 2, 1, z, false)) return null;
            for (int x : new int[]{-2, 2}) for (int y = 0; y <= 3; y++) {
                if (x == 2 && y == 1) continue;
                if (!frame(level, x, y, z)) return null;
            }
            for (int x = -1; x <= 1; x++) if (!frame(level, x, 3, z)) return null;
            positions.add(at(2, 1, z));
        }
        return new FactoryLayout(origin, forward, length, List.copyOf(positions));
    }

    private boolean stage(Level level, int x, int y, int z, boolean brake) {
        var block = level.getBlockState(at(x, y, z));
        return block.is(brake ? CEContinuousFactory.BRAKE.get() : CEContinuousFactory.STAND.get())
            && block.getValue(FactoryStageBlock.FACING) == forward.getCounterClockWise();
    }

    private boolean frame(Level level, int x, int y, int z) { return level.getBlockState(at(x, y, z)).is(CEContinuousFactory.FRAME.get()); }

    public boolean loaded(Level level) {
        return level.hasChunkAt(at(-3, 0, 0)) && level.hasChunkAt(at(3, 0, 0))
            && level.hasChunkAt(at(-3, 0, length)) && level.hasChunkAt(at(3, 0, length))
            && level.hasChunkAt(at(-3, 0, length / 2)) && level.hasChunkAt(at(3, 0, length / 2));
    }

    public boolean workingSpaceClear(Level level, FactoryKind kind) {
        var cursor = new BlockPos.MutableBlockPos();
        for (int z = 1; z < length; z++) {
            int bottom = kind == FactoryKind.KILN ? 2 : 1;
            int top = kind == FactoryKind.KILN ? 5 : kind == FactoryKind.MILL ? 2 : 3;
            int halfWidth = kind == FactoryKind.KILN ? 2 : 1;
            for (int x = -halfWidth; x <= halfWidth; x++) for (int y = bottom; y <= top; y++) {
                cursor.set(at(x, y, z));
                if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return false;
            }
        }
        return true;
    }
}
