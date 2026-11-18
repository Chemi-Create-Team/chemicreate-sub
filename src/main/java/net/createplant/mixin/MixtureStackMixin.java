package net.createplant.mixin;

import net.createplant.mixture.*;
import net.createplant.registry.CEMixtures;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native copying and removal must resize absolute component volumes together with the stack. */
@Mixin(value = FluidStack.class, remap = false)
public class MixtureStackMixin {
    @Shadow @Final private Fluid fluid;
    @Shadow private int amount;
    @Inject(method = "setAmount", at = @At("HEAD"))
    private void createPlant$resize(int next, CallbackInfo ci) {
        if (!(fluid instanceof MixtureFluid)) return;
        var self = (FluidStack) (Object) this;var contents = self.get(CEMixtures.MIXTURE_CONTENTS.get());if (contents == null) return;
        var resized = contents.resized(Math.max(0, next));self.set(CEMixtures.MIXTURE_CONTENTS.get(), resized);
        var data = self.get(CEMixtures.MIXTURE_DATA.get());if (data != null) self.set(CEMixtures.MIXTURE_DATA.get(), data.forContents(resized));
    }
    @Inject(method = "shrink", at = @At("HEAD"), cancellable = true)
    private void createPlant$remove(int count, CallbackInfo ci) {
        if (!(fluid instanceof MixtureFluid) || count < 0) return;
        var self = (FluidStack) (Object) this;var contents = self.get(CEMixtures.MIXTURE_CONTENTS.get());if (contents == null) return;
        var remaining = contents.split(Math.min(count, amount)).remaining();amount = remaining.totalAmount();self.set(CEMixtures.MIXTURE_CONTENTS.get(), remaining);
        var data = self.get(CEMixtures.MIXTURE_DATA.get());if (data != null) self.set(CEMixtures.MIXTURE_DATA.get(), data.forContents(remaining));ci.cancel();
    }
}
