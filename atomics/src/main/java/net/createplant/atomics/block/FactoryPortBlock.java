package net.createplant.atomics.block;

import net.createplant.atomics.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.atomics.factory.FactoryLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public final class FactoryPortBlock extends HorizontalDirectionalBlock {
    private static final com.mojang.serialization.MapCodec<FactoryPortBlock> CODEC = simpleCodec(FactoryPortBlock::new);
    @Override protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    public FactoryPortBlock(Properties properties) { super(properties); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }

    public static ContinuousFactoryBlockEntity controller(Level level, BlockPos port, BlockState state) {
        Direction forward = state.getValue(FACING);
        for (int z = 1; z <= FactoryLayout.MAX_LENGTH; z++) {
            var at = port.relative(forward.getOpposite(), z);
            if (!level.hasChunkAt(at)) return null;
            if (level.getBlockEntity(at) instanceof ContinuousFactoryBlockEntity machine && machine.facing() == forward)
                return machine.isOutlet(port) ? machine : null;
        }
        return null;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var machine = controller(level, pos, state);
        if (machine == null) return InteractionResult.PASS;
        if (!level.isClientSide) machine.takeOutput(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
