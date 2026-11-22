package net.createplant.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/** The empty stainless bucket for spouts, item drains and other fluid handlers; once filled, the liquid's own bucket wrapper takes over. */
public final class StainlessBucketWrapper extends FluidBucketWrapper {
    public StainlessBucketWrapper(ItemStack container) { super(container); }

    @Override public boolean canFillFluidType(FluidStack fluid) { return !StainlessBuckets.filled(fluid).isEmpty(); }
    @Override public FluidStack getFluid() { return FluidStack.EMPTY; }

    @Override protected void setFluid(FluidStack fluid) {
        container = fluid.isEmpty() ? StainlessBuckets.empty() : StainlessBuckets.filled(fluid);
    }
}
