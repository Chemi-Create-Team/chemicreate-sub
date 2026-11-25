package net.createplant.mixin;

import net.createplant.thermal.FluidIdentity;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FluidStack.class,remap=false)
public class FluidStateStackMixin {
    @Inject(method="<init>(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",at=@At("RETURN"))
    private void createPlant$migrate(Fluid registered,int amount,PatchedDataComponentMap components,CallbackInfo ci) {
        var stack=(FluidStack)(Object)this;
        FluidIdentity.migrate(stack);
    }
}
