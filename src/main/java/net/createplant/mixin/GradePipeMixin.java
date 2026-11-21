package net.createplant.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.createplant.transport.PipeMaterial;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Create's routing and filters for every fluid. A fluid the pipe is not rated for still flows and wears the pipe (see ContainmentWear). */
@Mixin(value = FluidTransportBehaviour.class, remap = false)
abstract class GradePipeMixin {
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"))
    private boolean createPlant$carrierFlow(FluidStack a, FluidStack b, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        return net.createplant.mixture.MixtureHelper.isMixture(a) && net.createplant.mixture.MixtureHelper.isMixture(b) || original.call(a, b);
    }

    @Inject(method = "canPullFluidFrom", at = @At("HEAD"), cancellable = true)
    private void createPlant$refuseUnsuitable(FluidStack fluid, BlockState state, Direction side, CallbackInfoReturnable<Boolean> result) {
        if (PipeMaterial.pipe(state) != null && net.createplant.mixture.MixtureHelper.isMixture(fluid) && !net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid))
            result.setReturnValue(false);
    }
}
