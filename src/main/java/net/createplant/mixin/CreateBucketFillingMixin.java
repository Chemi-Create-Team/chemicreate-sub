package net.createplant.mixin;

import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import net.createplant.item.StatefulBuckets;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GenericItemFilling.class, remap = false)
public abstract class CreateBucketFillingMixin {
    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true)
    private static void createplant$checkBucketRating(Level level, ItemStack stack, FluidStack fluid, CallbackInfoReturnable<Integer> cir) {
        if (!StatefulBuckets.emptyProcessBucket(stack)) return;
        // Create assumes its bucket wrapper accepts any fluid with a bucket item.
        int accepted = FluidUtil.getFluidHandler(stack.copyWithCount(1))
            .map(handler -> handler.fill(fluid, FluidAction.SIMULATE)).orElse(0);
        cir.setReturnValue(accepted > 0 ? accepted : -1);
    }
}
