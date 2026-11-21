package net.createplant.client;

import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CECrane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Opt-in frame-time comparison of the quarry gantry with and without a Flywheel backend, inactive during normal play. Four
 * 16 × 16 quarries dig a hundred blocks under their rails in a flat world; each state is timed twice per backend, in turn.
 */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class QuarryRenderClientCheck {
    private static final int RAIL_Y = 60, FLOOR_Y = -60, ORE_TOP_Y = -41, SIZE = 16, SPACING = 24, SETTLE_TICKS = 100, MEASURE_TICKS = 300;
    private static final List<BlockPos> DRIVES = List.of(new BlockPos(0, RAIL_Y, 0), new BlockPos(SPACING, RAIL_Y, 0), new BlockPos(0, RAIL_Y, SPACING), new BlockPos(SPACING, RAIL_Y, SPACING));
    private static final String[] BACKENDS = {"flywheel:off", "DEFAULT", "flywheel:off", "DEFAULT"};
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static int phase, ticks, run, frames, startFrames;
    private static long startNanos;

    private QuarryRenderClientCheck() { }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) { frames++; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("createplant.quarryRenderClientCheck")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) return;
        if (phase == 0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            mc.options.onboardAccessibility = false;mc.setScreen(new TitleScreen());
        }
        if (phase == 0 && mc.screen instanceof TitleScreen && ++ticks > 60) {
            phase = 1;mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("quarry-render-" + System.currentTimeMillis(),
                new net.minecraft.world.level.LevelSettings("Quarry render check", net.minecraft.world.level.GameType.CREATIVE, false,
                    net.minecraft.world.Difficulty.PEACEFUL, true, new net.minecraft.world.level.GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        }
        if (phase == 1 && mc.player != null && mc.level != null) {
            phase = 2;ticks = 0;mc.setScreen(null);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
            mc.options.enableVsync().set(false);mc.options.framerateLimit().set(260);mc.options.hideGui = true;
            mc.getSingleplayerServer().execute(() -> build(mc));
        }
        if (phase == 2 && ++ticks == 20) mc.getSingleplayerServer().execute(() -> {
            for (BlockPos drive : DRIVES) ((CreativeMotorBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(drive.south())).generatedSpeed.setValue(64);
        });
        if (phase == 2 && ticks > 6000) throw new IllegalStateException("The quarries did not reach the ore: " + states(mc));
        if (phase == 2 && ticks > 20 && deep(mc)) next(mc, 3);
        if (phase == 5 && ++ticks > SETTLE_TICKS && still(mc)) next(mc, 6);
        if ((phase == 3 || phase == 6) && ++ticks == SETTLE_TICKS) {
            startFrames = frames;startNanos = System.nanoTime();
        }
        if ((phase == 3 || phase == 6) && ticks == SETTLE_TICKS + MEASURE_TICKS) {
            record(mc, phase == 3);
            if (++run < BACKENDS.length) next(mc, phase);
            else if (phase == 3) {
                run = 0;phase = 5;ticks = 0;
                mc.getSingleplayerServer().execute(() -> {
                    for (BlockPos drive : DRIVES) mc.getSingleplayerServer().overworld().setBlockAndUpdate(drive.above(), Blocks.AIR.defaultBlockState());
                });
            } else {
                Files.writeString(mc.gameDirectory.toPath().resolve("quarry-render-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
                phase = 7;ticks = run = 0;
            }
        }
        // Close views of the first quarry's trolley and drill head, each without and with the backend, to compare the parts by eye.
        if (phase == 7 && ++ticks == 1) {
            if (!ClientCommandHandler.runCommand("flywheel backend " + BACKENDS[run % 2])) throw new IllegalStateException("Flywheel did not take the backend command");
            var quarry = shown(mc, DRIVES.getFirst());
            var gantry = QuarryRenderer.Gantry.of(quarry.visibleStructure(), DRIVES.getFirst(), quarry.renderedU(1), quarry.renderedV(1), quarry.renderedDepth(1));
            Vec3 target = Vec3.atLowerCornerOf(DRIVES.getFirst()).add(run < 2 ? gantry.trolley() : gantry.hook()).add(.5, .5, .5);
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).connection
                .teleport(target.x - 3, target.y + 2 - mc.player.getEyeHeight(), target.z - 3.5, -40.6f, 23.5f));
        }
        if (phase == 7 && ticks == 60) {
            Screenshot.grab(mc.gameDirectory, "quarry-render-" + (run < 2 ? "trolley-" : "head-") + run % 2 + ".png", mc.getMainRenderTarget(), message -> { });
            ticks = 0;
            if (++run == 4) { phase = 8;mc.stop(); }
        }
    }

    private static void build(Minecraft mc) {
        var level = mc.getSingleplayerServer().overworld();
        level.setDayTime(6000);
        for (BlockPos drive : DRIVES) {
            // Columns under the four rail ends keep the frame without legs, and the range below is open down to the ore.
            for (int x : new int[] {1, SIZE}) for (int z : new int[] {0, SIZE + 1}) for (int y = FLOOR_Y; y < RAIL_Y; y++)
                level.setBlock(drive.offset(x, y - RAIL_Y, z), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), 2);
            for (int x = 1; x <= SIZE; x++) for (int z = 1; z <= SIZE; z++) for (int y = FLOOR_Y; y <= ORE_TOP_Y; y++)
                level.setBlock(drive.offset(x, y - RAIL_Y, z), Blocks.STONE.defaultBlockState(), 2);
            level.setBlockAndUpdate(drive, CECrane.QUARRY_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
            level.setBlockAndUpdate(drive.south(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
            var quarry = (QuarryDriveBlockEntity) level.getBlockEntity(drive);
            quarry.size(SIZE, SIZE);
            for (var stack : List.of(AllBlocks.METAL_GIRDER.asStack(64), AllBlocks.MECHANICAL_DRILL.asStack(), AllItems.BELT_CONNECTOR.asStack(2),
                AllBlocks.SHAFT.asStack(8), AllBlocks.ENCASED_CHAIN_DRIVE.asStack(32)))
                if (!ItemHandlerHelper.insertItem(quarry.store(), stack, false).isEmpty()) throw new IllegalStateException("The drive did not take " + stack);
            level.setBlockAndUpdate(drive.above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        }
        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.getAbilities().flying = true;player.onUpdateAbilities();
        player.connection.teleport(-34.5, 6, -34.5, -45, 0);
    }

    private static QuarryDriveBlockEntity shown(Minecraft mc, BlockPos drive) {
        return mc.level.getBlockEntity(drive) instanceof QuarryDriveBlockEntity quarry && quarry.visibleStructure() != null ? quarry : null;
    }

    private static boolean digging(QuarryDriveBlockEntity quarry) { return quarry.status().equals("moving") || quarry.status().equals("mining"); }

    private static boolean deep(Minecraft mc) {
        for (BlockPos drive : DRIVES) {
            var quarry = shown(mc, drive);
            if (quarry == null || !digging(quarry) || quarry.renderedDepth(1) < RAIL_Y - ORE_TOP_Y - 1) return false;
        }
        return true;
    }

    private static boolean still(Minecraft mc) {
        for (BlockPos drive : DRIVES) {
            var quarry = shown(mc, drive);
            if (quarry == null || digging(quarry) || quarry.renderedU(0) != quarry.renderedU(1) || quarry.renderedV(0) != quarry.renderedV(1)
                || quarry.renderedDepth(0) != quarry.renderedDepth(1)) return false;
        }
        return true;
    }

    private static String states(Minecraft mc) {
        var states = new StringBuilder();
        for (BlockPos drive : DRIVES) {
            var quarry = mc.level.getBlockEntity(drive) instanceof QuarryDriveBlockEntity found ? found : null;
            states.append(quarry == null ? "missing" : quarry.status() + "@" + quarry.renderedDepth(1)).append(' ');
        }
        return states.toString();
    }

    private static void next(Minecraft mc, int measuring) {
        phase = measuring;ticks = 0;
        if (!ClientCommandHandler.runCommand("flywheel backend " + BACKENDS[run])) throw new IllegalStateException("Flywheel did not take the backend command");
    }

    private static void record(Minecraft mc, boolean working) {
        double seconds = (System.nanoTime() - startNanos) / 1e9, depth = 0;
        int counted = frames - startFrames;
        boolean instanced = VisualizationManager.supportsVisualization(mc.level);
        for (BlockPos drive : DRIVES) {
            var quarry = shown(mc, drive);
            if (quarry == null || digging(quarry) != working) throw new IllegalStateException("A quarry left the measured state: " + states(mc));
            if (instanced && !(((dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage) ((dev.engine_room.flywheel.impl.visualization.VisualManagerImpl<?, ?>)
                VisualizationManager.getOrThrow(mc.level).blockEntities()).getStorage()).visualAtPos(drive.asLong()) instanceof QuarryVisual))
                throw new IllegalStateException("Missing quarry visual at " + drive);
            depth += quarry.renderedDepth(1) / DRIVES.size();
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("state", working ? "working" : "standing");
        result.put("backend", Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString());
        result.put("quarries", DRIVES.size());
        result.put("meanDepth", depth);
        result.put("seconds", seconds);
        result.put("frames", counted);
        result.put("fps", counted / seconds);
        result.put("frameMillis", seconds * 1000 / counted);
        RESULTS.add(result);
        Screenshot.grab(mc.gameDirectory, "quarry-render-" + result.get("state") + "-" + run + ".png", mc.getMainRenderTarget(), message -> { });
        org.slf4j.LoggerFactory.getLogger(QuarryRenderClientCheck.class).info("QUARRY RENDER {}", result);
    }
}
