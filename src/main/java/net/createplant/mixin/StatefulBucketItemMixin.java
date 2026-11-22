package net.createplant.mixin;

import net.createplant.item.StainlessBuckets;
import net.createplant.item.StatefulBuckets;
import net.createplant.thermal.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class StatefulBucketItemMixin extends Item {
    @Shadow @Final public Fluid content;

    protected StatefulBucketItemMixin(Properties properties) { super(properties); }

    @Override public Component getName(ItemStack stack) {
        var name=super.getName(stack);
        if(content==Fluids.EMPTY)return name;
        var fluid=StatefulBuckets.contents(stack,content);
        if(StatefulBuckets.defaultState(fluid) && !FluidState.gas(fluid))return name;
        return Component.translatable("fluid_state.create_refinery_n_reactors.bucket",name,
            FluidState.phaseName(fluid),FluidState.temperatureName(FluidState.temperature(fluid)),FluidState.pressureName(FluidState.pressure(fluid)));
    }

    @Override public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return StainlessBuckets.marked(stack) ? StainlessBuckets.empty() : super.getCraftingRemainingItem(stack);
    }

    @Inject(method="getEmptySuccessItem",at=@At("RETURN"),cancellable=true)
    private static void createPlant$stainlessShell(ItemStack bucket,Player player,CallbackInfoReturnable<ItemStack> ci) {
        if(ci.getReturnValue().is(Items.BUCKET) && StainlessBuckets.marked(bucket))ci.setReturnValue(StainlessBuckets.empty());
    }

    @Inject(method="emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
        at=@At("HEAD"),cancellable=true)
    private void createPlant$checkState(Player player,Level level,BlockPos pos,BlockHitResult hit,ItemStack item,CallbackInfoReturnable<Boolean> ci) {
        if(content==Fluids.EMPTY || item==null)return;
        var fluid=StatefulBuckets.contents(item,content);
        if(FluidState.gas(fluid))ci.setReturnValue(StatefulBuckets.release(level,pos,fluid));
        else if(!StatefulBuckets.defaultState(fluid))ci.setReturnValue(false);
    }
}
