package net.createplant.nuclear;

import net.createplant.registry.CERadiation;
import net.createplant.safety.RadiationHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** Real radiation behaviour behind the core {@link RadiationHooks}; installed by the atomics addon. */
public final class RadiationHooksImpl implements RadiationHooks {
    @Override public void spill(Level level, BlockPos pos, FluidStack fluid) { RadiationContamination.spill(level, pos, fluid); }

    @Override public void stampWorldFluid(ServerLevel level, BlockPos pos, FluidStack fluid) {
        var material = RadiationWorldData.get(level).blockActivity.get(pos);
        if (material != null) fluid.set(CERadiation.MATERIAL.get(), material);
    }

    @Override public void stamp(FluidStack fluid) { RadiationSources.stamp(fluid); }

    @Override public boolean tracked(FluidStack fluid) { return RadiationSources.profile(fluid) != null; }

    @Override public void mix(FluidStack result, FluidStack first, FluidStack second, int added, long now) {
        RadiationSources.mix(result, first, second, added, now);
    }

    @Override public boolean dirty(Player player) { return RadiationContamination.dirty(player); }

    @Override public void wash(ServerPlayer player, double fraction) { RadiationContamination.wash(player, fraction); }

    @Override public BlockPos washBlock(ServerLevel level, BlockPos clicked, BlockPos beside) {
        var target = RadiationContamination.at(level, beside).total() <= 0 ? clicked : beside;
        var removed = RadiationContamination.take(level, target, .9);
        if (removed.total() <= 0) return null;
        RadiationContamination.deposit(level, target.below(), removed);
        return target;
    }
}
