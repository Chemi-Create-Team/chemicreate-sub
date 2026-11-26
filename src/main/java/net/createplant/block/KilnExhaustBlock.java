package net.createplant.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class KilnExhaustBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 4, 14), box(5, 4, 5, 11, 13, 11), box(3, 13, 3, 13, 16, 13));
    public KilnExhaustBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}
