package net.createplant.atomics.mixin;

import net.createplant.atomics.factory.FactoryWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class FactoryBlockChangesMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void createPlant$factoryChanged(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> result) {
        if (result.getReturnValue() != null && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level)
            FactoryWatch.changed(level, pos);
    }
}
