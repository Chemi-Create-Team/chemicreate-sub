package net.createplant.mixin;

import net.createplant.item.StatefulBuckets;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidIdentity;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public class LegacyBucketStackMixin {
    @Shadow @Final @Mutable private Item item;
    @Shadow @Final @Mutable PatchedDataComponentMap components;

    @Inject(method="<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",at=@At("RETURN"))
    private void createPlant$migrateBucket(ItemLike original,int amount,PatchedDataComponentMap data,CallbackInfo ci) {
        if(amount<=0 || !(item instanceof BucketItem bucket) || !FluidIdentity.legacy(bucket.content))return;
        var stack=(ItemStack)(Object)this;
        var fluid=StatefulBuckets.contents(stack,bucket.content);
        var replacement=FluidUtil.getFilledBucket(fluid);
        if(replacement.isEmpty() || replacement.is(item))return;
        item=replacement.getItem();
        components=PatchedDataComponentMap.fromPatch(item.components(),data.asPatch());
        components.set(CEWorldFluids.BUCKET_CONTENTS.get(),replacement.get(CEWorldFluids.BUCKET_CONTENTS.get()));
    }
}
