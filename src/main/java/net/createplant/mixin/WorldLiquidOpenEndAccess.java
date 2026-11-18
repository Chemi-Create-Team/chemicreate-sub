package net.createplant.mixin;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = OpenEndedPipe.class, remap = false)
public interface WorldLiquidOpenEndAccess {
    @Invoker("removeFluidFromSpace") FluidStack createPlant$removeFluid(boolean simulate);
    @Invoker("provideFluidToSpace") boolean createPlant$provideFluid(FluidStack fluid, boolean simulate);
    @Accessor("wasPulling") void createPlant$setPulling(boolean pulling);
}
