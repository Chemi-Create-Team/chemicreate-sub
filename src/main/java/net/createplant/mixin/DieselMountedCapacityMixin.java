package net.createplant.mixin;

import com.jesz.createdieselgenerators.content.oil_barrel.OilBarrelMountedStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve contents while a CDG mounted barrel exceeds its current capacity. */
@Mixin(value=OilBarrelMountedStorage.Handler.class,remap=false)
public abstract class DieselMountedCapacityMixin extends FluidTank {
    protected DieselMountedCapacityMixin(int capacity){super(capacity);}
    @Override public int fill(FluidStack incoming,FluidAction action){return getFluidAmount()>=getCapacity() ? 0 : super.fill(incoming,action);}
}
