package net.createplant.client;

import com.google.gson.GsonBuilder;

import net.createplant.nuclear.RadiationSources;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CERadiation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in world check of server-owned readings, hand models and the beta-window control. */
final class RadiationClientCheck {
    private static int ticks;
    private static double farRate;
    private static volatile Throwable failure;
    private static final Map<String, Object> readings = new LinkedHashMap<>();

    private RadiationClientCheck() {}

    static void start(Minecraft mc) {
        ticks = 0;
        readings.clear();
        failure = null;
        mc.setScreen(null);
        mc.options.hideGui = false;
        mc.options.pauseOnLostFocus = false;
        mc.gameRenderer.setRenderHand(true);
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                var server = mc.getSingleplayerServer();
                                var level = server.overworld();
                                level.setDayTime(6000);
                                for (int x = -6; x <= 6; x++)
                                    for (int z = -5; z <= 9; z++)
                                        level.setBlockAndUpdate(
                                                new BlockPos(x, 80, z),
                                                Blocks.SMOOTH_STONE.defaultBlockState());
                                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                                player.setGameMode(GameType.CREATIVE);
                                player.setItemInHand(
                                        InteractionHand.MAIN_HAND,
                                        CERadiation.SURVEY_METER.toStack());
                                var source =
                                        new ItemEntity(
                                                level,
                                                .5,
                                                82.62,
                                                4.5,
                                                RadiationSources.stamp(
                                                        CENuclear.SPENT_FUEL_ROD.toStack(), level));
                                source.setNoGravity(true);
                                source.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                                source.setPickUpDelay(32767);
                                source.setUnlimitedLifetime();
                                level.addFreshEntity(source);
                                player.connection.teleport(.5, 81, -.5, 0, 0);
                            } catch (Throwable e) {
                                failure = e;
                            }
                        });
    }

    static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException("Radiation client fixture", failure);
        if (++ticks == 100) {
            farRate = mc.player.getData(CERadiation.EXPOSURE).getDouble("GammaSvS");
            if (!(farRate > .04 && farRate < .07))
                throw new IllegalStateException(
                        "The real client must receive the five-metre gamma field: " + farRate);
            readings.put("fiveMetreGammaSvS", farRate);
        }
        if (ticks == 140)
            mc.getSingleplayerServer()
                    .execute(
                            () ->
                                    mc.getSingleplayerServer()
                                            .getPlayerList()
                                            .getPlayer(mc.player.getUUID())
                                            .connection
                                            .teleport(.5, 81, 3.5, 0, 0));
        if (ticks == 220) {
            double rate = mc.player.getData(CERadiation.EXPOSURE).getDouble("GammaSvS");
            if (rate < farRate * 24 || rate > farRate * 26)
                throw new IllegalStateException(
                        "The client field must follow inverse-square distance");
            readings.put("oneMetreGammaSvS", rate);
        }
        if (ticks == 240)
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                var player =
                                        mc.getSingleplayerServer()
                                                .getPlayerList()
                                                .getPlayer(mc.player.getUUID());
                                player.getMainHandItem()
                                        .use(player.level(), player, InteractionHand.MAIN_HAND);
                            });
        if (ticks == 300) {
            var data = mc.player.getData(CERadiation.EXPOSURE);
            if (!mc.player.getMainHandItem().getOrDefault(CERadiation.BETA_WINDOW.get(), false)
                    || data.getDouble("AmbientBetaSvS") <= 0)
                throw new IllegalStateException(
                        "The beta window and the owner-only field must synchronize");
            readings.put("betaWindow", true);
            readings.put("oneMetreBetaSvS", data.getDouble("AmbientBetaSvS"));
        }
        if (ticks == 340)
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                var player =
                                        mc.getSingleplayerServer()
                                                .getPlayerList()
                                                .getPlayer(mc.player.getUUID());
                                player.setItemInHand(
                                        InteractionHand.OFF_HAND, player.getMainHandItem());
                                player.setItemInHand(
                                        InteractionHand.MAIN_HAND, CERadiation.DOSIMETER.toStack());
                                player.connection.teleport(.5, 81, -.5, 0, 0);
                            });
        if (ticks == 440) {
            var data = mc.player.getData(CERadiation.EXPOSURE);
            if (!mc.player.getMainHandItem().is(CERadiation.DOSIMETER.get())
                    || data.getDouble("LifetimeSv") <= 0
                    || !Double.isFinite(data.getDouble("LifetimeSv")))
                throw new IllegalStateException(
                        "The badge must display the accumulated owner-only server dose");
            var tooltip =
                    mc.player
                            .getMainHandItem()
                            .getTooltipLines(
                                    net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                                    mc.player,
                                    net.minecraft.world.item.TooltipFlag.NORMAL);
            var expected =
                    net.createplant.item.RadiationMeterItem.reading(
                                    mc.player.getMainHandItem(), data)
                            .getString();
            if (tooltip.stream().noneMatch(line -> line.getString().equals(expected)))
                throw new IllegalStateException(
                        "The client tooltip must use the owner's synchronized readings");
            readings.put("ownerOnlyTooltip", true);
            readings.put("lifetimeSv", data.getDouble("LifetimeSv"));
            readings.put("badgeCurrentGammaSvS", data.getDouble("GammaSvS"));
        }
        if (ticks < Integer.getInteger("createplant.radiationClientHold", 1200)) return false;
        Files.writeString(
                mc.gameDirectory.toPath().resolve("radiation-client-result.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(readings));
        return true;
    }
}
