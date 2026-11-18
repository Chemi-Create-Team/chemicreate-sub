package net.createplant.mixin;

import net.createplant.registry.CEWorldFluids;
import net.createplant.transport.FluidHazards;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidBucketWrapper.class, remap = false)
public class FluidBucketWrapperMixin {
    @Shadow protected ItemStack container;

    @Inject(method = "canFillFluidType", at = @At("HEAD"), cancellable = true)
    private void createPlant$corrosion(FluidStack fluid, CallbackInfoReturnable<Boolean> callback) {
        if (container.is(Items.BUCKET) && CEWorldFluids.isLiquid(fluid.getFluid()) && FluidHazards.of(fluid).corrosive())
            callback.setReturnValue(false);
    }
}
