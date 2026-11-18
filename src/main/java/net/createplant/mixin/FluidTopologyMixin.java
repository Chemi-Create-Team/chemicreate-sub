package net.createplant.mixin;

import com.simibubi.create.content.fluids.FluidPropagator;
import net.createplant.thermal.FluidRoutes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FluidPropagator.class,remap=false)
public class FluidTopologyMixin {
    @Inject(method="propagateChangedPipe",at=@At("HEAD"))
    private static void createPlant$changed(LevelAccessor world,BlockPos pos,BlockState state,CallbackInfo ci) {
        if (world instanceof Level level) FluidRoutes.changed(level);
    }
}
