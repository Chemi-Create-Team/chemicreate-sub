package net.createplant.mixin;

import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import net.createplant.transport.NozzleIntake;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NozzleBlock.class)
public class NozzleBlockMixin {
    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void createPlant$allowPipeMount(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (NozzleIntake.supported(level, pos, state.getValue(NozzleBlock.FACING))) callback.setReturnValue(true);
    }
}
