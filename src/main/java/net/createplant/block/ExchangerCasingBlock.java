package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** The one block of a fluid heat exchanger's run; it lies along the way the player looks when placing it. */
public class ExchangerCasingBlock extends ThermalCasingBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, ExchangerStructure.SEGMENTS * ExchangerStructure.PLACES - 1);

    public ExchangerCasingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FORMED, false).setValue(CELL, 0).setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FORMED, CELL, FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override protected void changed(Level level, BlockPos pos) { ExchangerStructure.changed(level, pos, this); }
}
