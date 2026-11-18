package net.createplant.mixin;

import com.jesz.createdieselgenerators.content.distillation.DistillationControllerItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=DistillationControllerItem.class,remap=false)
public class DieselDistillationControllerMixin {
    @Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private void createPlant$disableConversion(UseOnContext context,CallbackInfoReturnable<InteractionResult> cir) {
        cir.setReturnValue(InteractionResult.FAIL);
    }
}
