package net.createplant.atomics.mixin;

import net.createplant.atomics.nuclear.RadiationSources;
import net.createplant.atomics.nuclear.RadiationWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public class RadiationBucketMixin {
    @Inject(
            method =
                    "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("RETURN"))
    private void keepCoolingHistory(
            Player player,
            Level level,
            BlockPos pos,
            BlockHitResult hit,
            ItemStack container,
            CallbackInfoReturnable<Boolean> ci) {
        if (!ci.getReturnValue()
                || !(level instanceof ServerLevel server)
                || container == null
                || RadiationSources.profile(level.getBlockState(pos)) == null) return;
        var fluid = FluidUtil.getFluidContained(container);
        if (fluid.isEmpty() || RadiationSources.profile(fluid.get()) == null) return;
        var data = RadiationWorldData.get(server);
        data.blockActivity.put(
                pos.immutable(),
                RadiationSources.material(fluid.get(), RadiationSources.now(level)));
        data.blocks.add(pos.immutable());
        data.setDirty();
    }
}
