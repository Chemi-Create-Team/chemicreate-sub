package net.createplant.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** The arm's item: once placed, the points marked with it go to the new arm, as with Create's mechanical arm. */
public class IndustrialArmItem extends BlockItem {
    public IndustrialArmItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() && context.getLevel().isClientSide) net.createplant.client.ArmPointSelection.flush(context.getClickedPos());
        return result;
    }
}
