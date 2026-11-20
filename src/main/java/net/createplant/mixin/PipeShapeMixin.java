package net.createplant.mixin;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.createplant.transport.PipeConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidPipeBlock.class, remap = false)
abstract class PipeShapeMixin {
    @Inject(method = "canConnectTo", at = @At("HEAD"), cancellable = true)
    private static void createPlant$checkJoint(BlockAndTintGetter world, BlockPos neighbor, BlockState state, Direction side,
            CallbackInfoReturnable<Boolean> result) {
        if (PipeConnections.isClosed(world, neighbor, side.getOpposite())) result.setReturnValue(false);
    }

    @Inject(method = "updateBlockState", at = @At("RETURN"), cancellable = true)
    private void createPlant$keepEndsClosed(BlockState state, Direction preferred, Direction ignored, BlockAndTintGetter world, BlockPos pos,
            CallbackInfoReturnable<BlockState> result) {
        // Create can add a straight open end back after canConnectTo has returned false.
        var updated = PipeConnections.apply(world, pos, result.getReturnValue());
        if (updated != result.getReturnValue()) result.setReturnValue(updated);
    }
}
