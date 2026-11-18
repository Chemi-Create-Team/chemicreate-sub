package net.createplant.mixin;

import net.createplant.block.ColumnLadder;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets entities climb the ladder of a distillation column, which is not a ladder block they stand in. */
@Mixin(value=LivingEntity.class,remap=false)
public class LivingEntityMixin {
    @Inject(method="onClimbable",at=@At("HEAD"),cancellable=true)
    private void createPlant$columnLadder(CallbackInfoReturnable<Boolean> cir) {
        var entity=(LivingEntity)(Object)this;
        if (!entity.isSpectator() && ColumnLadder.isLadderAt(entity.level(),entity.blockPosition())) cir.setReturnValue(true);
    }
}
