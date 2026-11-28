package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;

/** The invisible ring of a column section. It draws nothing, holds no state and leads to the section at its centre. */
public abstract class ColumnRingBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ColumnRingBlock(Properties properties) { super(properties.pushReaction(PushReaction.BLOCK)); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    /** The section this ring cell belongs to, or null when the chain is broken. */
    public static BlockPos centre(BlockGetter level, BlockPos pos, BlockState state) {
        for (int step = 0; step < 2 && state.getBlock() instanceof ColumnRingBlock; step++) {
            pos = pos.relative(state.getValue(FACING));
            state = level.getBlockState(pos);
        }
        return state.getBlock() instanceof ColumnSectionBlock ? pos : null;
    }

    @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (side == state.getValue(FACING) && !(neighbour.getBlock() instanceof ColumnRingBlock || neighbour.getBlock() instanceof ColumnSectionBlock))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override public BlockState playerWillDestroy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos centre = centre(level, pos, state);
        if (centre != null) level.destroyBlock(centre, !player.isCreative(), player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos centre = centre(level, pos, state);
        return centre == null ? ItemStack.EMPTY : new ItemStack(level.getBlockState(centre).getBlock());
    }
}
