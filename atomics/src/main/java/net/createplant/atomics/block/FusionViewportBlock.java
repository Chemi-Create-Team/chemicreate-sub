package net.createplant.atomics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A quartz-glass pane in a steel frame that may stand in for any vacuum vessel wall, so the plasma can be watched. */
public class FusionViewportBlock extends FusionPartBlock {
    public FusionViewportBlock(Properties properties) { super(properties); }

    @Override protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, side);
    }

    @Override protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }

    @Override protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1; }

    @Override protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
}
