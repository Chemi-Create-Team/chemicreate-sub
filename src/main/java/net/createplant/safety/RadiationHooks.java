package net.createplant.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** Core hook for radioactive contamination. The default does nothing; the atomics addon installs the real one. */
public interface RadiationHooks {
    RadiationHooks NONE = new RadiationHooks() { };
    Holder INSTANCE = new Holder();

    final class Holder {
        public volatile RadiationHooks hooks = NONE;
    }

    static RadiationHooks get() { return INSTANCE.hooks; }

    static void install(RadiationHooks hooks) { INSTANCE.hooks = hooks; }

    default void spill(Level level, BlockPos pos, FluidStack fluid) { }

    /** Marks a fluid taken from the world with the activity stored at pos. */
    default void stampWorldFluid(ServerLevel level, BlockPos pos, FluidStack fluid) { }

    default void stamp(FluidStack fluid) { }

    default boolean tracked(FluidStack fluid) { return false; }

    default void mix(FluidStack result, FluidStack first, FluidStack second, int added, long now) { }

    default boolean dirty(Player player) { return false; }

    default void wash(ServerPlayer player, double fraction) { }

    /** Moves contamination for soap use; returns the position it was taken from, or null when there was none. */
    default BlockPos washBlock(ServerLevel level, BlockPos clicked, BlockPos beside) { return null; }
}
