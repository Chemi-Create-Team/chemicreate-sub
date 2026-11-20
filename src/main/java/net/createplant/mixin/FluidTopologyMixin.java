package net.createplant.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;
import net.createplant.thermal.FluidRoutes;
import net.createplant.transport.PipeConnections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FluidPropagator.class,remap=false)
public class FluidTopologyMixin {
    @Inject(method="propagateChangedPipe",at=@At("HEAD"))
    private static void createPlant$changed(LevelAccessor world,BlockPos pos,BlockState state,CallbackInfo ci) {
        if (world instanceof Level level) FluidRoutes.changed(level);
    }

    @Inject(method="isOpenEnd",at=@At("HEAD"),cancellable=true)
    private static void createPlant$sealedJoint(BlockGetter world,BlockPos pos,Direction side,CallbackInfoReturnable<Boolean> result) {
        if (PipeConnections.isClosed(world,pos,side) && PipeConnections.pipe(world.getBlockState(pos))) result.setReturnValue(false);
    }
}
