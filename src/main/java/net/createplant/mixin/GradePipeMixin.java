package net.createplant.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.createplant.transport.FluidHazards;
import net.createplant.transport.PipeMaterial;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A pipe does not take in a fluid its material cannot carry: too hot, too cold, too high a pressure grade, or corrosive to it. Create's
 * own valves and smart pipes refuse flows the same way, so the network simply does not reach past such a pipe.
 */
@Mixin(value = FluidTransportBehaviour.class, remap = false)
abstract class GradePipeMixin {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"))
    private boolean createPlant$carrierFlow(FluidStack a, FluidStack b, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        return net.createplant.mixture.MixtureHelper.isMixture(a) && net.createplant.mixture.MixtureHelper.isMixture(b) || original.call(a, b);
    }

    @Inject(method = "canPullFluidFrom", at = @At("HEAD"), cancellable = true)
    private void createPlant$refuseUnsuitable(FluidStack fluid, BlockState state, Direction side, CallbackInfoReturnable<Boolean> result) {
        var material = PipeMaterial.pipe(state);
        if (material != null && !fluid.isEmpty() && !material.carries(fluid)) result.setReturnValue(false);
    }
}
