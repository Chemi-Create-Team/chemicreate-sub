package net.createplant.mixin;

import net.createplant.nuclear.RadiationSources;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Different cooling ages must mix without losing isotope inventory, including native Create tanks.
 */
@Mixin(value = FluidTank.class, remap = false)
public class RadiationFluidMixMixin {
    @Unique private FluidStack radiationBefore = FluidStack.EMPTY;

    @Inject(method = "fill", at = @At("HEAD"))
    private void radiationBeforeFill(
            FluidStack fluid, FluidAction action, CallbackInfoReturnable<Integer> ci) {
        radiationBefore =
                action.execute() && RadiationSources.profile(fluid) != null
                        ? ((FluidTank) (Object) this).getFluid().copy()
                        : FluidStack.EMPTY;
    }

    @Inject(method = "fill", at = @At("RETURN"))
    private void radiationAfterFill(
            FluidStack fluid, FluidAction action, CallbackInfoReturnable<Integer> ci) {
        if (!action.execute()
                || ci.getReturnValue() <= 0
                || RadiationSources.profile(fluid) == null) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread())
            RadiationSources.mix(
                    ((FluidTank) (Object) this).getFluid(),
                    radiationBefore,
                    fluid,
                    ci.getReturnValue(),
                    server.overworld().getGameTime());
        radiationBefore = FluidStack.EMPTY;
    }
}
