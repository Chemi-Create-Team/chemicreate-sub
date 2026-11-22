package net.createplant.mixin;

import net.createplant.client.FluidStateVisuals;
import net.createplant.item.StainlessBuckets;
import net.createplant.item.StatefulBuckets;
import net.createplant.thermal.FluidIdentity;
import net.createplant.thermal.FluidState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class StatefulBucketModelMixin {
    @Inject(method="getModel",at=@At("RETURN"),cancellable=true)
    private void createPlant$fillBucket(ItemStack item,Level level,LivingEntity entity,int seed,CallbackInfoReturnable<BakedModel> ci) {
        if(!(item.getItem() instanceof BucketItem bucket))return;
        if(StainlessBuckets.marked(item)) {
            ci.setReturnValue(Minecraft.getInstance().getModelManager().getModel(FluidStateVisuals.STAINLESS_BUCKET));
            return;
        }
        if(!FluidIdentity.controlled(bucket.content))return;
        var fluid=StatefulBuckets.contents(item,bucket.content);
        if(fluid.isEmpty() || StatefulBuckets.defaultState(fluid) && !FluidState.gas(fluid))return;
        var id=FluidState.gas(fluid) ? FluidStateVisuals.GAS_BUCKET : FluidStateVisuals.LIQUID_BUCKET;
        ci.setReturnValue(Minecraft.getInstance().getModelManager().getModel(id));
    }
}
