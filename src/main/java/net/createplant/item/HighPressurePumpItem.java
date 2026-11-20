package net.createplant.item;

import net.createplant.block.HighPressurePumpBlock;
import net.createplant.block.PressurePumpStructure;
import net.createplant.block.PressurePumpStructure.Part;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class HighPressurePumpItem extends BlockItem {
    public HighPressurePumpItem(Block block, Properties properties) { super(block, properties); }

    @Override public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return BlockPlaceContext.at(context, context.getClickedPos().above(), Direction.UP);
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var drive = context.getClickedPos();
        var front = state.getValue(HighPressurePumpBlock.FACING);
        for (var part : Part.values()) {
            var pos = part.at(drive, front);
            if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                    || context.getPlayer() != null && !level.mayInteract(context.getPlayer(), pos)
                    || !level.isUnobstructed(part.state(front), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
        }
        if (!super.placeBlock(context, state)) return false;
        for (var part : Part.values()) {
            if (part != Part.DRIVE) level.setBlock(part.at(drive, front), part.state(front), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(drive) instanceof net.createplant.block.entity.HighPressurePumpBlockEntity pump)
            pump.loadFromItem(context.getItemInHand());
        PressurePumpStructure.refresh(level, drive, front);
        return true;
    }
}
