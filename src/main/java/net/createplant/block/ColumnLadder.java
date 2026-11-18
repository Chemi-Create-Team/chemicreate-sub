package net.createplant.block;

import com.simibubi.create.AllBlocks;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The ladder drawn on the west wall of a distillation column, in the south-most cell of that wall on every section but the base, and the one up
 * a lidded vessel's side. Each is climbed from the block beside it, which holds no ladder of its own, so the climbing check asks the wall instead.
 */
public final class ColumnLadder {
    private static final String CLIMBED = "createplant_ladder_climbed", LAST_Y = "createplant_ladder_y";
    private static final double STEP = .6;

    private ColumnLadder() { }

    public static boolean isLadderAt(Level level, BlockPos entityPos) {
        for (Direction side : Iterate.horizontalDirections) {
            BlockPos at = entityPos.relative(side);
            if (!level.hasChunkAt(at)) continue;
            BlockState vessel = level.getBlockState(at);
            // A vessel's ladder faces away from its hinge, so it is climbed from the block on that side.
            if (vessel.getBlock() instanceof LiddedVesselBlock && vessel.getValue(LiddedVesselBlock.LADDER) && vessel.getValue(LiddedVesselBlock.HINGE) == side) return true;
        }
        BlockPos wall = entityPos.east();
        if (!level.hasChunkAt(wall)) return false;
        BlockState state = level.getBlockState(wall);
        return state.getBlock() instanceof FractionatingColumnBlock
            && state.getValue(FractionatingColumnBlock.SECTION) != FractionatingColumnBlock.Section.BASE
            && state.getValue(FractionatingColumnBlock.CELL) == (state.getValue(FractionatingColumnBlock.WIDTH) - 1) * 3;
    }

    /** Vanilla's step sound comes from the block under the feet, so the climbing sound of Create's andesite ladder is played here. */
    public static void climbSound(PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.level().isClientSide()) return;
        var data = player.getPersistentData();
        if (!isLadderAt(player.level(), player.blockPosition()) || player.isSpectator()) { data.remove(CLIMBED); return; }
        double moved = data.contains(CLIMBED) ? Math.abs(player.getY() - data.getDouble(LAST_Y)) : 0;
        data.putDouble(LAST_Y, player.getY());
        double total = data.getDouble(CLIMBED) + moved;
        if (total < STEP) { data.putDouble(CLIMBED, total); return; }
        data.putDouble(CLIMBED, 0);
        var sound = AllBlocks.ANDESITE_LADDER.getDefaultState().getSoundType();
        player.level().playSound(null, player.blockPosition(), sound.getStepSound(), SoundSource.PLAYERS, sound.getVolume() * .15f, sound.getPitch());
    }
}
