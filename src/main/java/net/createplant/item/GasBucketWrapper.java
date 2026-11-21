package net.createplant.item;

import net.createplant.transport.FluidHazards;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/** Retains the grades and mixture properties of a gas while it is in a container. */
public final class GasBucketWrapper extends FluidBucketWrapper {
    public GasBucketWrapper(ItemStack container) { super(container); }
    @Override public boolean canFillFluidType(FluidStack fluid) { return FluidHazards.of(fluid).gas(); }
    @Override protected void setFluid(FluidStack fluid) {
        container = fluid.isEmpty() ? new ItemStack(Items.BUCKET) : net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(fluid);
    }
}
