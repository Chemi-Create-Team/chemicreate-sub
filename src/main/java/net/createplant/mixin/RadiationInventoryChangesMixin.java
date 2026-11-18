package net.createplant.mixin;

import net.createplant.nuclear.RadiationField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dirty inventory notifications are the shared invalidation path for vanilla and Create storage.
 */
@Mixin(BlockEntity.class)
public class RadiationInventoryChangesMixin {
    @Inject(
            method =
                    "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private static void radiationInventoryChanged(
            Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        RadiationField.dirty(level, pos);
    }

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void radiationLoaded(Level level, CallbackInfo ci) {
        RadiationField.dirty(level, ((BlockEntity) (Object) this).getBlockPos());
    }
}
