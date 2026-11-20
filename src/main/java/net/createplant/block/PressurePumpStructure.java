package net.createplant.block;

import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.registry.CEPressureInfrastructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PressurePumpStructure {
    public enum Part implements StringRepresentable {
        DRIVE(0, 0, 0), SUCTION(0, -1, 1), RECEIVE(0, -1, 0), FEED(0, 0, 1),
        LOWER_FRONT(1, -1, 0), LOWER_BACK(1, -1, 1), UPPER_FRONT(1, 0, 0), UPPER_BACK(1, 0, 1);

        private final int width, height, depth;
        Part(int width, int height, int depth) { this.width = width; this.height = height; this.depth = depth; }
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }

        public BlockPos at(BlockPos drive, Direction front) {
            return drive.relative(front.getClockWise(), width).relative(front.getOpposite(), depth).above(height);
        }

        public BlockPos drive(BlockPos pos, Direction front) {
            return pos.relative(front.getCounterClockWise(), width).relative(front, depth).below(height);
        }

        public BlockState state(Direction front) {
            if (this == DRIVE || this == SUCTION)
                return (this == DRIVE ? CEPressureInfrastructure.PUMP.get() : CEPressureInfrastructure.INTAKE.get())
                    .defaultBlockState().setValue(HighPressurePumpBlock.FACING, front);
            return CEPressureInfrastructure.CASING.get().defaultBlockState()
                .setValue(PressurePumpCasingBlock.PART, this).setValue(PressurePumpCasingBlock.FACING, front);
        }
    }

    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    private PressurePumpStructure() { }

    public static boolean complete(Level level, BlockPos drive, Direction front) {
        for (var part : Part.values()) {
            var pos = part.at(drive, front);
            if (!level.hasChunkAt(pos) || level.getBlockState(pos) != part.state(front)) return false;
        }
        return true;
    }

    public static void refresh(Level level, BlockPos drive, Direction front) {
        for (var part : Part.values()) {
            var pos = part.at(drive, front);
            level.invalidateCapabilities(pos);
            if (level.getBlockEntity(pos) instanceof HighPressurePumpBlockEntity pump) pump.updatePressureChange();
        }
    }

    public static void dismantle(Level level, BlockPos at, Part part, Direction front, boolean moving) {
        if (!(level instanceof ServerLevel server) || REMOVING.get() || moving || level.restoringBlockSnapshots) return;
        BlockPos drive = part.drive(at, front);
        if (!level.hasChunkAt(drive) || !(level.getBlockEntity(drive) instanceof HighPressurePumpBlockEntity root)) return;
        root.disconnectForRemoval();
        var inlet = Part.SUCTION.at(drive, front);
        if (level.hasChunkAt(inlet) && level.getBlockEntity(inlet) instanceof HighPressurePumpBlockEntity suction) {
            if (root.creativeBreak()) suction.setCreativeBreak();
            suction.disconnectForRemoval();
        }

        REMOVING.set(true);
        try {
            for (var piece : Part.values()) {
                var pos = piece.at(drive, front);
                if (pos.equals(at) || !level.hasChunkAt(pos)) continue;
                if (level.getBlockState(pos) == piece.state(front)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            REMOVING.remove();
        }
        if (!root.creativeBreak() && server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)) {
            var stack = CEPressureInfrastructure.PUMP_ITEM.toStack();
            root.storeInItem(stack);
            Containers.dropItemStack(server, at.getX()+.5, at.getY()+.5, at.getZ()+.5, stack);
        }
    }

    public static void creativeBreak(Level level, BlockPos at, Part part, Direction front, boolean creative) {
        if (creative && level.getBlockEntity(part.drive(at, front)) instanceof HighPressurePumpBlockEntity pump) pump.setCreativeBreak();
    }
}
