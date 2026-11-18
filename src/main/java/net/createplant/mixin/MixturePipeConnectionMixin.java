package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.fluids.PipeConnection;
import net.createplant.mixture.MixtureHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PipeConnection.class, remap = false)
public class MixturePipeConnectionMixin {
    @WrapOperation(method = "manageFlows", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"))
    private boolean createPlant$carrierFlow(FluidStack a, FluidStack b, Operation<Boolean> original) {
        return MixtureHelper.isMixture(a) && MixtureHelper.isMixture(b) || original.call(a, b);
    }
}
