package net.createplant.mixin;

import net.createplant.client.FluidStateVisuals;
import net.createplant.item.StainlessBuckets;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A stainless bucket filled with any liquid, other mods' included, tints its fill layer although its own bucket item may have no colour handler. */
@Mixin(ItemColors.class)
public class StainlessBucketColorMixin {
    @Inject(method="getColor",at=@At("HEAD"),cancellable=true)
    private void createPlant$stainlessFill(ItemStack stack,int tintIndex,CallbackInfoReturnable<Integer> ci) {
        if(tintIndex==1 && StainlessBuckets.marked(stack))ci.setReturnValue(FluidStateVisuals.stainlessBucketTint(stack));
    }
}
