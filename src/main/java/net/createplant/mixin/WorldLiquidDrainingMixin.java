package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BlockPosEntry;
import it.unimi.dsi.fastutil.PriorityQueue;
import net.createplant.registry.CEWorldFluids;
import net.createplant.transport.WorldLiquidDrainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FluidDrainingBehaviour.class, remap = false)
public class WorldLiquidDrainingMixin implements WorldLiquidDrainer {
    @Shadow private PriorityQueue<BlockPosEntry> queue;
    @Shadow private Fluid fluid;

    @Unique @Override public boolean createPlant$processLiquid() { return fluid != null && CEWorldFluids.isLiquid(fluid); }

    @WrapOperation(method = "pullNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean createPlant$previewKeepsWorld(Level level, BlockPos pos, BlockState state, int flags,
        Operation<Boolean> original, @Local(argsOnly = true) boolean simulate) {
        return simulate && fluid != null && CEWorldFluids.isLiquid(fluid) || original.call(level, pos, state, flags);
    }
}
