package net.createplant.transport;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import net.createplant.registry.CEFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class NozzleIntake {
    private static final int MAX_DRAW = 1000;

    private NozzleIntake() { }

    public static boolean supported(LevelReader level, BlockPos pos, Direction mouth) {
        var rear = pos.relative(mouth.getOpposite());
        if (level.isOutsideBuildHeight(rear) || !level.hasChunkAt(rear)) return false;
        var state = level.getBlockState(rear);
        var block = state.getBlock();
        if (block instanceof PumpBlock) return PumpBlock.isOpenAt(state, mouth);
        if (block instanceof IAxisPipe pipe) return pipe.getAxis(state) == mouth.getAxis();
        return block instanceof FluidPipeBlock || block instanceof EncasedPipeBlock;
    }

    public static IFluidHandler port(Level level, BlockPos pos, BlockState state, BlockEntity entity, Direction side) {
        Direction mouth = state.getValue(NozzleBlock.FACING);
        return side == mouth.getOpposite() && supported(level, pos, mouth) ? new Intake(level, pos, mouth) : null;
    }

    private record Intake(Level level, BlockPos pos, Direction mouth) implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? drain(MAX_DRAW, FluidAction.SIMULATE) : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? MAX_DRAW : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack stack, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            return stack.is(CEFluids.AIR_GAS.get()) ? drain(stack.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || !level.hasChunkAt(pos)) return FluidStack.EMPTY;
            var state = level.getBlockState(pos);
            if (!AllBlocks.NOZZLE.has(state) || state.getValue(NozzleBlock.FACING) != mouth || !supported(level, pos, mouth))
                return FluidStack.EMPTY;
            var opening = pos.relative(mouth);
            return !level.isOutsideBuildHeight(opening) && level.hasChunkAt(opening) && level.getBlockState(opening).isAir()
                ? new FluidStack(CEFluids.AIR_GAS.get(), Math.min(amount, MAX_DRAW)) : FluidStack.EMPTY;
        }
    }
}
