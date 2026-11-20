package net.createplant.client;

import net.createplant.CreatePlant;
import net.createplant.item.PipeConfiguratorItem;
import net.createplant.registry.CEPortableTools;
import net.createplant.transport.PipeArm;
import net.createplant.transport.PipeConnections;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class PipeConfiguratorOverlay {
    private static final int CONNECTED = 0xf2ce83, CUT = 0xe18c62;
    private static final Object TARGET = new Object();
    private enum Marker {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;
        private static final Marker[] ALL = values();
    }

    private static PipeArm arm;

    private PipeConfiguratorOverlay() { }

    @SubscribeEvent public static void select(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var outliner = Outliner.getInstance();
        for (Marker key : Marker.ALL) outliner.remove(key);
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui
                || !mc.player.isHolding(CEPortableTools.PIPE_CONFIGURATOR.get())) {
            arm = null;
            outliner.remove(TARGET);
            return;
        }
        arm = PipeArm.target(mc.level, mc.player);
        BlockPos at = arm != null ? arm.pipe() : mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null;
        if (at != null) {
            var state = mc.level.getBlockState(at);
            for (Direction side : Direction.values()) {
                if (arm != null && arm.side() == side || !PipeConnections.isClosed(mc.level, at, side)
                        || !PipeConnections.canToggle(mc.level, at, side)) continue;
                var bounds = PipeArm.bounds(mc.level, at, state, side);
                if (bounds != null) outliner.showAABB(Marker.ALL[side.get3DDataValue()], bounds).colored(CUT).lineWidth(.012f).disableLineNormals();
            }
        }
        if (arm == null) outliner.remove(TARGET);
        else outliner.showAABB(TARGET, arm.bounds().inflate(.002)).colored(arm.cut() ? CUT : CONNECTED).lineWidth(.03f).disableLineNormals();
    }

    @SubscribeEvent public static void outline(RenderHighlightEvent.Block event) {
        if (arm != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void prompt(RenderGuiEvent.Post event) {
        if (arm == null) return;
        var mc = Minecraft.getInstance();
        var text = Component.translatable(PipeConfiguratorItem.KEY + (arm.cut() ? "join_hint" : "cut_hint"));
        int x = (mc.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2;
        event.getGuiGraphics().drawString(mc.font, text, x, mc.getWindow().getGuiScaledHeight() / 2 + 13, arm.cut() ? CUT : CONNECTED, true);
    }
}
