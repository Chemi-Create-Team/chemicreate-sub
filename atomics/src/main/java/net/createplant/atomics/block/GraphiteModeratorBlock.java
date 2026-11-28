package net.createplant.atomics.block;

import net.createplant.atomics.nuclear.ReactorCores;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Graphite moderator of a reactor core. It holds nothing, so its placement and removal only notify the core it touches. */
public class GraphiteModeratorBlock extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    public GraphiteModeratorBlock(Properties properties) { super(properties); }

    @Override public BlockPos getInformationSource(Level level, BlockPos pos, BlockState state) { return ReactorCores.controllerUnder(level, pos); }

    /** Graphite, once alight, keeps burning like netherrack until it is put out. */
    @Override public boolean isFireSource(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, net.minecraft.core.Direction direction) {
        return direction == net.minecraft.core.Direction.UP;
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(state.getBlock())) ReactorCores.changed(level, pos);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!newState.is(state.getBlock())) ReactorCores.changed(level, pos);
    }
}
