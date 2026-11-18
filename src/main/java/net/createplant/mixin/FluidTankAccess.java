package net.createplant.mixin;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = FluidTank.class, remap = false)
public interface FluidTankAccess {
    @Accessor("fluid") void createPlant$setFluid(FluidStack stack);
    @Invoker("onContentsChanged") void createPlant$contentsChanged();
}
