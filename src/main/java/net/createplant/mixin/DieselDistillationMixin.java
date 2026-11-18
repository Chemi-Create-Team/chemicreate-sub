package net.createplant.mixin;

import com.jesz.createdieselgenerators.content.distillation.DistillationTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A saved or command-placed CDG tower must not bypass the refinery. */
@Mixin(value=DistillationTankBlockEntity.class,remap=false)
public class DieselDistillationMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void createPlant$disableDistillation(CallbackInfo ci) { ci.cancel(); }
}
