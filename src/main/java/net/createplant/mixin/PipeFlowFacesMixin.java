package net.createplant.mixin;

import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.createplant.transport.PipeConnections;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
    "com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity$StandardPipeFluidTransportBehaviour",
    "com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity$StraightPipeFluidTransportBehaviour"
}, remap = false)
abstract class PipeFlowFacesMixin {
    @Inject(method = "canHaveFlowToward", at = @At("RETURN"), cancellable = true)
    private void createPlant$checkJoint(BlockState state, Direction side, CallbackInfoReturnable<Boolean> result) {
        var pipe = (FluidTransportBehaviour) (Object) this;
        if (result.getReturnValueZ() && PipeConnections.pipe(state) && PipeConnections.isClosed(pipe.getWorld(), pipe.getPos(), side))
            result.setReturnValue(false);
    }
}
