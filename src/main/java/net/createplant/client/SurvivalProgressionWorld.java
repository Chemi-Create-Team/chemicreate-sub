package net.createplant.client;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.createplant.CreatePlant;
import net.createplant.gametest.SurvivalMetalsWorkshop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Commission the saved survival workshop using Minecraft's own machine ticks. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class SurvivalProgressionWorld {
    private static final boolean ENABLED = Boolean.getBoolean("createplant.survivalProgressionWorld");
    private static final Logger LOGGER = LoggerFactory.getLogger(SurvivalProgressionWorld.class);
    private static final String SAVE = "survival-progression-" + System.currentTimeMillis();
    private static int phase, titleTicks;
    private static SurvivalMetalsWorkshop workshop;
    private static MinecraftServer server;
    private static volatile Map<String, Object> results;

    private SurvivalProgressionWorld() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (phase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++titleTicks > 40) {
            phase = 1;
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel(SAVE,
                new LevelSettings("序盤〜中盤のサバイバル確認", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(42L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                    .value().createWorldDimensions(), mc.screen);
        } else if (phase == 1 && mc.player != null && mc.getOverlay() == null) {
            phase = 2;
            server = mc.getSingleplayerServer();
            server.execute(SurvivalProgressionWorld::build);
        } else if (phase == 2 && results != null) {
            phase = 3;
            try {
                var path = mc.gameDirectory.toPath().resolve("survival-progression-results.json");
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(results));
            } catch (IOException error) {
                LOGGER.error("Survival results could not be saved", error);
            }
        }
    }

    private static void build() {
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        for (int x = 0; x <= 101; x++) for (int z = 0; z <= 82; z++)
            level.setBlockAndUpdate(new BlockPos(x, -59, z), Blocks.STONE_BRICKS.defaultBlockState());
        level.setDefaultSpawnPos(new BlockPos(25, -58, 29), 180);
        var player = server.getPlayerList().getPlayers().getFirst();
        player.connection.teleport(25.5, -58, 29.5, 180, -30);
        try {
            workshop = new SurvivalMetalsWorkshop(level, new BlockPos(0, -59, 0));
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SurvivalProgressionWorld::tick);
            int sprint = Integer.getInteger("createplant.survivalProgressionSprint", 0);
            if (sprint > 0) server.tickRateManager().requestGameToSprint(sprint);
        } catch (RuntimeException error) {
            finish(error);
        }
    }

    private static void tick(ServerTickEvent.Post event) {
        if (results != null || event.getServer() != server) return;
        try {
            if (workshop.tick()) finish(null);
            else if (Boolean.getBoolean("createplant.survivalNormalChemistry") && workshop.measuringSulfurCombustion()
                && server.tickRateManager().isSprinting()) {
                server.tickRateManager().stopSprinting();
                LOGGER.info("SURVIVAL workshop is measuring air intake and sulfur combustion at normal speed");
            }
        } catch (RuntimeException error) {
            finish(error);
        }
    }

    private static void finish(RuntimeException error) {
        var measured = new LinkedHashMap<String, Object>();
        measured.put("world", SAVE);
        if (workshop != null) measured.putAll(workshop.measurements());
        measured.put("equipment", equipment());
        if (error != null) {
            measured.put("stalled", error.getMessage());
            LOGGER.error("SURVIVAL WORKSHOP STALLED", error);
        } else {
            LOGGER.info("SURVIVAL WORKSHOP {}", measured);
        }
        if (server.tickRateManager().isSprinting()) server.tickRateManager().stopSprinting();
        server.saveEverything(false, true, true);
        results = measured;
    }

    private static Map<String, Integer> equipment() {
        var level = server.overworld();
        var tally = new TreeMap<String, Integer>();
        for (var at : BlockPos.betweenClosed(0, -58, 0, 101, -23, 82)) {
            var state = level.getBlockState(at);
            if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LAVA)) continue;
            tally.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
        }
        return tally;
    }
}
