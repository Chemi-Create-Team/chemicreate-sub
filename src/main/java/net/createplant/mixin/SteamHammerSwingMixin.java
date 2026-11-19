package net.createplant.mixin;

import net.createplant.client.SteamHammerClient;
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
public abstract class SteamHammerSwingMixin {
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void createplant$slam(LivingEntity entity, float age, CallbackInfo ci) {
        if (!(entity instanceof Player) || !SteamHammerClient.swinging(entity)) return;
        var model = (HumanoidModel<?>)(Object)this;
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, right);
        float wind = SteamHammerClient.windup(model.attackTime), strike = SteamHammerClient.downswing(model.attackTime);
        float angle = -1.12f * wind + .92f * strike;
        model.rightArm.xRot += angle;
        model.leftArm.xRot += angle;
        model.rightArm.z -= 2 * strike;
        model.leftArm.z -= 2 * strike;
        ci.cancel();
    }
}
