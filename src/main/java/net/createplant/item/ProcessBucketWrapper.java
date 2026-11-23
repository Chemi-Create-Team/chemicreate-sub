package net.createplant.item;

import net.createplant.registry.CEWorldFluids;
import net.createplant.transport.FluidHazards;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

public final class ProcessBucketWrapper extends FluidBucketWrapper {
    public ProcessBucketWrapper(ItemStack container) { super(container); }

    private boolean alloy() {
        return container.is(CEWorldFluids.EMPTY_ALLOY_BUCKET.get())
            || container.getItem() instanceof ProcessBucketItem bucket && bucket.alloy();
    }

    @Override public boolean canFillFluidType(FluidStack fluid) {
        return CEWorldFluids.isLiquid(fluid.getFluid()) && FluidHazards.of(fluid).corrosive() == alloy();
    }

    @Override public FluidStack getFluid() {
        return container.getItem() instanceof ProcessBucketItem bucket ? bucket.contents(container) : FluidStack.EMPTY;
    }

    @Override public FluidStack getFluidInTank(int tank) { return tank == 0 ? getFluid() : FluidStack.EMPTY; }
    @Override public int getTankCapacity(int tank) { return tank == 0 ? 1000 : 0; }
    @Override public boolean isFluidValid(int tank, FluidStack fluid) { return tank == 0 && canFillFluidType(fluid); }

    @Override protected void setFluid(FluidStack fluid) {
        if (fluid.isEmpty() && container.getItem() instanceof ProcessBucketItem bucket) container = bucket.emptyBucket(container);
        else if (fluid.isEmpty()) container = new ItemStack(alloy() ? CEWorldFluids.EMPTY_ALLOY_BUCKET.get() : Items.BUCKET);
        else container = CEWorldFluids.bucket(fluid);
    }
}
