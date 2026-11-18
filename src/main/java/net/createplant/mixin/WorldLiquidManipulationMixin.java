package net.createplant.mixin;

import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour;
import net.createplant.registry.CEWorldFluids;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidManipulationBehaviour.class, remap = false)
public class WorldLiquidManipulationMixin {
    @Inject(method = "canDrainInfinitely", at = @At("HEAD"), cancellable = true)
    private void createPlant$finiteProcessLiquid(Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if (fluid != null && CEWorldFluids.isLiquid(fluid)) cir.setReturnValue(false);
    }
}
