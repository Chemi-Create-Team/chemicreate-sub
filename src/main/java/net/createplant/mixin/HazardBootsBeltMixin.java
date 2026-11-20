package net.createplant.mixin;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltMovementHandler;
import net.createplant.safety.HazardSuit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BeltMovementHandler.class, remap = false)
abstract class HazardBootsBeltMixin {
    @Inject(method = "canBeTransported", at = @At("HEAD"), cancellable = true)
    private static void createPlant$grip(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && HazardSuit.boots(living)) cir.setReturnValue(false);
    }
    @Inject(method = "transportEntity", at = @At("HEAD"), cancellable = true)
    private static void createPlant$gripWhenEquipped(BeltBlockEntity belt, Entity entity,
            BeltMovementHandler.TransportedEntityInfo info, CallbackInfo ci) {
        if (entity instanceof LivingEntity living && HazardSuit.boots(living)) ci.cancel();
    }
}
