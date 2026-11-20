package net.createplant.mixin;

import net.createplant.agriculture.FertilizedCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class FertilizedCropChangesMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void createPlant$cropChanged(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> result) {
        var chunk = (LevelChunk) (Object) this;
        BlockState previous = result.getReturnValue();
        if (previous != null && chunk.getLevel() instanceof ServerLevel level)
            FertilizedCrops.replaced(level, pos, previous, state);
    }
}
