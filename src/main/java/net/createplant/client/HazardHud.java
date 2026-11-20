package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasClouds;
import net.createplant.registry.CEPressureInfrastructure;
import net.createplant.safety.HazardHudConfig;
import net.createplant.safety.HazardSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class HazardHud {
    private static boolean toxicNearby;
    private HazardHud() { }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(CreatePlant.location("hazard_visor"), (graphics, delta) -> render(graphics));
        }
    }

    @EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
    public static final class Samples {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            var player = Minecraft.getInstance().player;
            if (player == null || !player.getItemBySlot(EquipmentSlot.HEAD).is(CEPressureInfrastructure.HELMET.get())) {
                toxicNearby = false;
                return;
            }
            if (player.tickCount % 10 != 0) return;
            toxicNearby = GasClouds.in(player.level(), AABB.ofSize(player.getEyePosition(), 16, 16, 16)).stream()
                .anyMatch(GasCloud::toxic);
        }
    }

    private static void render(GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (!HazardHudConfig.SPEC.isLoaded() || !HazardHudConfig.ENABLED.get() || mc.player == null
                || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;
        var helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!helmet.is(CEPressureInfrastructure.HELMET.get())) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight(), inset = 4;
        int length = Math.min(25, width / 12), seal = 0x88292D2D;
        for (int x : new int[]{inset, width-inset-length}) {
            for (int y : new int[]{inset, height-inset-length}) {
                int edgeX = x == inset ? x : x+length-2;
                int edgeY = y == inset ? y : y+length-2;
                graphics.fill(x, edgeY, x+length, edgeY+2, seal);
                graphics.fill(edgeX, y, edgeX+2, y+length, seal);
            }
        }
        boolean spent = helmet.getDamageValue() >= helmet.getMaxDamage();
        int color = spent ? 0xFFE48C70 : toxicNearby ? 0xFFE5BC5D : 0xFF8DC0A4;
        var status = Component.translatable(HazardSuit.KEY + (spent ? "spent" : toxicNearby ? "warning" : "clear"));
        graphics.fill(width-inset-7, inset+6, width-inset-3, inset+10, color);
        graphics.drawString(mc.font, status, width-inset-11-mc.font.width(status), inset+4, color, true);
    }
}
