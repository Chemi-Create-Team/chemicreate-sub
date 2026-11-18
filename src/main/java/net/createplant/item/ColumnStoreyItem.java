package net.createplant.item;

import net.createplant.block.ExtractionColumnStructureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** A 3 × 3 storey clicked onto the top of a storey of the same column lands centred over it, wherever on the deck it was clicked. */
public class ColumnStoreyItem extends BlockItem {
    public ColumnStoreyItem(Block block, Properties properties) { super(block, properties); }

    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP || context.replacingClickedOnBlock()) return context;
        var level = context.getLevel();
        BlockPos below = context.getClickedPos().below();
        var state = level.getBlockState(below);
        BlockPos centre = state.is(getBlock()) ? below : ExtractionColumnStructureBlock.centre(level, below, state);
        return centre == null || !level.getBlockState(centre).is(getBlock()) ? context : BlockPlaceContext.at(context, centre.above(), Direction.UP);
    }
}
