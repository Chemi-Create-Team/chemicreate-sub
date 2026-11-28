package net.createplant.logistics.item;

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
        if (!result.consumesAction()) return result;
        if (context.getLevel().isClientSide) net.createplant.logistics.client.ArmPointSelection.flush(context.getClickedPos());
        else if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) net.createplant.logistics.arm.ArmPoints.placed(player, context.getClickedPos());
        return result;
    }
}
