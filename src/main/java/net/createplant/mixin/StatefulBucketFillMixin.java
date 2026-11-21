package net.createplant.mixin;

import net.createplant.item.StatefulBuckets;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FluidUtil.class,remap=false)
public class StatefulBucketFillMixin {
    @Inject(method="getFilledBucket",at=@At("RETURN"))
    private static void createPlant$copyFluidState(FluidStack fluid,CallbackInfoReturnable<ItemStack> ci) {
        StatefulBuckets.write(fluid,ci.getReturnValue());
    }
}
