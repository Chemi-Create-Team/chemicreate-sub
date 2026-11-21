package net.createplant.client;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.createplant.CreatePlant;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.createplant.transport.PipeMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** What a fluid is and what a pipe carries, in grades: on this mod's buckets, on pipe items, and on Create's own pipes seen through goggles. */
public final class PipeQualityOverlay {
    private static final String PREFIX = "transport." + CreatePlant.MOD_ID + ".";

    public static void tooltip(ItemTooltipEvent event) {
        var item = event.getItemStack().getItem();
        if (item instanceof net.minecraft.world.item.BucketItem bucket
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID)) {
            var chemical = FluidHazards.of(new net.neoforged.neoforge.fluids.FluidStack(bucket.content, 1));
            event.getToolTip().add(Component.translatable(PREFIX + "grade", Grade.describe(chemical.heat(), chemical.pressure())));
            if (chemical.corrosive()) event.getToolTip().add(Component.translatable(PREFIX + "corrosive." + chemical.corrosion().name().toLowerCase(java.util.Locale.ROOT)));
            if (chemical.gas()) event.getToolTip().add(Component.translatable(PREFIX + "gas"));
        }
        if (item instanceof BlockItem block && PipeMaterial.pipe(block.getBlock().defaultBlockState()) instanceof PipeMaterial material)
            material.describe(event.getToolTip(), net.neoforged.neoforge.fluids.FluidStack.EMPTY, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    /** Create's own pipes have no goggle readout of their own; this mod's pipes show the same lines through Create's goggle interface. */
    public static void goggles(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !GogglesItem.isWearingGoggles(mc.player)
            || !(mc.hitResult instanceof BlockHitResult hit)) return;
        var state = mc.level.getBlockState(hit.getBlockPos());
        var material = PipeMaterial.pipe(state);
        if (material == null || !material.createPipe() || mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation) return;
        List<Component> lines = new ArrayList<>();
        material.describe(lines, PipeMaterial.contents(mc.level.getBlockEntity(hit.getBlockPos())), mc.player.isShiftKeyDown());
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 20, y = mc.getWindow().getGuiScaledHeight() / 2 + 8;
        for (var line : lines) { event.getGuiGraphics().drawString(mc.font, line, x, y, 0xE9E6CF, true); y += 10; }
    }

    private PipeQualityOverlay() { }
}
