package net.createplant.mixin;

import net.createplant.mixture.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidTank.class, remap = false)
public class MixtureTankMixin {
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createPlant$acceptCarrier(FluidStack resource, FluidAction action, CallbackInfoReturnable<Integer> cir) {
        var tank = (FluidTank) (Object) this;
        if (MixtureHelper.isMixture(resource) || MixtureHelper.isMixture(tank.getFluid())) cir.setReturnValue(MixtureTransfer.fill(tank, resource, action, false));
    }
    @Inject(method = "drain(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;", at = @At("HEAD"), cancellable = true)
    private void createPlant$drainCarrier(FluidStack request, FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        var tank = (FluidTank) (Object) this;
        var stored = tank.getFluid();
        if (MixtureHelper.isMixture(request) && MixtureHelper.isMixture(stored))
            cir.setReturnValue(stored.is(request.getFluid()) ? tank.drain(request.getAmount(), action) : FluidStack.EMPTY);
    }
}
