package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.registry.CEPressureInfrastructure;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class PressureInfrastructureTooltips {
    private PressureInfrastructureTooltips() { }

    @SubscribeEvent public static void add(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        String type;
        if (stack.is(CEPressureInfrastructure.PUMP_ITEM.get())) type = "pump";
        else if (stack.is(CEPressureInfrastructure.HOSE.get())) type = "hose";
        else if (stack.is(CEPressureInfrastructure.CONNECTOR.asItem())) type = "connector";
        else return;
        String key = "tooltip." + CreatePlant.MOD_ID + ".pressure_infrastructure.";
        var lines = event.getToolTip();
        lines.add(Component.translatable(key + type).withStyle(ChatFormatting.GRAY));
        if (!Screen.hasShiftDown()) {
            lines.add(Component.translatable(key + "shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        lines.add(Component.translatable(key + type + "_detail").withStyle(ChatFormatting.GRAY));
        if (type.equals("pump")) lines.add(Component.translatable(key + "pump_ports").withStyle(ChatFormatting.GRAY));
    }
}
