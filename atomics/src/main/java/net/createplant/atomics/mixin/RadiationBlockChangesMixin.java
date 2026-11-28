package net.createplant.atomics.mixin;

import net.createplant.atomics.nuclear.RadiationField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Block changes from machines and explosions have no single NeoForge placement event. */
@Mixin(LevelChunk.class)
public class RadiationBlockChangesMixin {
    @Shadow @Final private Level level;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void radiationChanged(
            BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> ci) {
        RadiationField.blockChanging(level, pos, state);
    }
}
