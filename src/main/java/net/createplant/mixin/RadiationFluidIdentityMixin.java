package net.createplant.mixin;

import net.createplant.nuclear.RadiationSources;
import net.createplant.registry.CERadiation;
import net.neoforged.neoforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidStack.class, remap = false)
public class RadiationFluidIdentityMixin {
    @Inject(method = "isSameFluidSameComponents", at = @At("HEAD"), cancellable = true)
    private static void radiationAgeIsNotAFluidType(
            FluidStack a, FluidStack b, CallbackInfoReturnable<Boolean> ci) {
        if (a.isEmpty()
                || b.isEmpty()
                || a.getFluid() != b.getFluid()
                || RadiationSources.profile(a) == null
                || !a.has(CERadiation.MATERIAL.get()) && !b.has(CERadiation.MATERIAL.get())) return;
        var first = a.copy();
        var second = b.copy();
        first.remove(CERadiation.MATERIAL.get());
        second.remove(CERadiation.MATERIAL.get());
        ci.setReturnValue(FluidStack.isSameFluidSameComponents(first, second));
    }
}
