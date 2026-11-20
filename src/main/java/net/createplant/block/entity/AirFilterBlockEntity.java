package net.createplant.block.entity;

import net.createplant.registry.CEFluids;
import net.createplant.registry.CEGasPipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class AirFilterBlockEntity extends BlockEntity {
    private static final int MAX_DRAW = 1000;
    private static final Direction[] FACES = Direction.values();

    public AirFilterBlockEntity(BlockPos pos, BlockState state) { super(CEGasPipes.AIR_FILTER_ENTITY.get(),pos,state); }

    public IFluidHandler intake(Direction connection) {
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? drain(MAX_DRAW,FluidAction.SIMULATE) : FluidStack.EMPTY; }
            @Override public int getTankCapacity(int tank) { return tank == 0 ? MAX_DRAW : 0; }
            @Override public boolean isFluidValid(int tank,FluidStack stack) { return false; }
            @Override public int fill(FluidStack stack,FluidAction action) { return 0; }
            @Override public FluidStack drain(FluidStack stack,FluidAction action) {
                return stack.is(CEFluids.AIR_GAS.get()) ? drain(stack.getAmount(),action) : FluidStack.EMPTY;
            }
            @Override public FluidStack drain(int amount,FluidAction action) {
                if (amount <= 0 || level == null || isRemoved()) return FluidStack.EMPTY;
                for (Direction face : FACES) {
                    if (face == connection) continue;
                    var outside = worldPosition.relative(face);
                    if (!level.isOutsideBuildHeight(outside) && level.hasChunkAt(outside) && level.getBlockState(outside).isAir())
                        return new FluidStack(CEFluids.AIR_GAS.get(),Math.min(amount,MAX_DRAW));
                }
                return FluidStack.EMPTY;
            }
        };
    }
}
