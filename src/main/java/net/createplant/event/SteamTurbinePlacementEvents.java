package net.createplant.event;

import net.createplant.CreatePlant;
import net.createplant.block.HighPressureTurbinePartBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID)
public final class SteamTurbinePlacementEvents {
    private SteamTurbinePlacementEvents() {}

    @SubscribeEvent
    public static void onWrenchWindow(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var state = level.getBlockState(pos);
        if (event.getEntity().isShiftKeyDown()
                && event.getItemStack().is(Tags.Items.TOOLS_WRENCH)
                && (state.getBlock() instanceof HighPressureTurbinePartBlock
                        || state.getBlock()
                                instanceof net.createplant.block.HighPressureTurbineEndBlock
                        || state.getBlock() instanceof net.createplant.block.SteamTurbineBlock)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
            if (!level.isClientSide())
                net.createplant.block.TurbineUnit.remove(
                        level, pos, !event.getEntity().isCreative());
            return;
        }
        if (!(state.getBlock() instanceof HighPressureTurbinePartBlock)
                || !event.getItemStack().is(Tags.Items.TOOLS_WRENCH)
                || state.getValue(HighPressureTurbinePartBlock.SECTION)
                || !HighPressureTurbinePartBlock.canHaveWindow(state)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        if (!level.isClientSide())
            level.setBlock(pos, state.cycle(HighPressureTurbinePartBlock.WINDOW), Block.UPDATE_ALL);
    }
}
