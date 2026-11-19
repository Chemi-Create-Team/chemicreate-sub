package net.createplant.mixin;

import net.createplant.client.SteamGunClient;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class SteamGunThrustMixin {
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void createplant$thrust(LivingEntity entity, float age, CallbackInfo ci) {
        if (!(entity instanceof Player) || !SteamGunClient.thrusting(entity)) return;
        var model = (HumanoidModel<?>)(Object)this;
        float reach = 3 * SteamGunClient.thrustAmount(model.attackTime);
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, entity.getMainArm() == HumanoidArm.RIGHT);
        model.rightArm.z -= reach;
        model.leftArm.z -= reach;
        ci.cancel();
    }
}
