package net.createplant.mixin;

import java.util.function.Predicate;
import com.simibubi.create.content.fluids.FlowSource;
import net.createplant.mixture.MixtureHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A one-mB preview cannot describe all constituents or validate their transport grades. */
@Mixin(value = FlowSource.class, remap = false)
public class MixtureFlowSourceMixin {
    @Inject(method = "provideFluid", at = @At("HEAD"), cancellable = true)
    private void createPlant$wholeComposition(Predicate<FluidStack> allowed, CallbackInfoReturnable<FluidStack> cir) {
        var provider = ((FlowSource) (Object) this).provideHandler();if (provider == null) return;
        var handler = provider.getCapability();if (handler == null) return;
        boolean skippedMixture = false;
        for (int i = 0; i < handler.getTanks(); i++) {
            var candidate = handler.getFluidInTank(i);if (candidate.isEmpty()) continue;
            if (MixtureHelper.isMixture(candidate)) {
                if (allowed.test(candidate)) { cir.setReturnValue(candidate.copy());return; }
                skippedMixture = true;
            } else if (allowed.test(candidate)) {
                if (skippedMixture) cir.setReturnValue(candidate.copyWithAmount(1));return;
            }
        }
        if (skippedMixture) cir.setReturnValue(FluidStack.EMPTY);
    }
}
