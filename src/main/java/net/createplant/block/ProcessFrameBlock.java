package net.createplant.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Inert frame segments take their geometry from their position in the assembled vessel. */
public class ProcessFrameBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    public ProcessFrameBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(PART, 0).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(PART, BlockStateProperties.HORIZONTAL_FACING); }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        boolean alongX = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.X;
        return switch (state.getValue(PART)) {
            case 0 -> Block.box(6, 0, 6, 10, 16, 10);
            case 1 -> alongX ? Block.box(0, 5, 6, 16, 11, 10) : Block.box(6, 5, 0, 10, 11, 16);
            case 2 -> alongX ? Block.box(6, 5, 0, 10, 11, 16) : Block.box(0, 5, 6, 16, 11, 10);
            case 6 -> alongX ? Block.box(0, 5, 5, 16, 13, 13) : Block.box(5, 5, 0, 13, 13, 16);
            default -> net.minecraft.world.phys.shapes.Shapes.block();
        };
    }
}
