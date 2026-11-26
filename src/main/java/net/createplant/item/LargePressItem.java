package net.createplant.item;

import net.createplant.block.LargePressFootprint;
import net.createplant.block.LargePressFrameBlock;
import net.createplant.registry.CELargePress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

/** A press placed against a large depot snaps to its centre and leaves room for the platen. */
public final class LargePressItem extends BlockItem {
    public LargePressItem(Properties properties) { super(CELargePress.PRESS.get(), properties); }

    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP || context.replacingClickedOnBlock()) return context;
        var level = context.getLevel();
        BlockPos surface = context.getClickedPos().below();
        var state = level.getBlockState(surface);
        BlockPos center = state.is(CELargePress.FRAME.get()) ? LargePressFrameBlock.center(level, surface, state) : surface;
        if (center == null || !level.getBlockState(center).is(CELargePress.DEPOT.get())) return context;
        return LargePressFootprint.openWorkingSpace(level, center.above()) ? BlockPlaceContext.at(context, center.above(2), Direction.UP) : null;
    }
}
