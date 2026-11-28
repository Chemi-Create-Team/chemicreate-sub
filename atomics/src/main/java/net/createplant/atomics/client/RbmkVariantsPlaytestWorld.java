package net.createplant.atomics.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.block.entity.RodCapBlockEntity;
import net.createplant.atomics.nuclear.ReactorCore;
import net.createplant.atomics.nuclear.ReactorCoreStructure;
import net.createplant.atomics.nuclear.ReactorCoreVariants;
import net.createplant.atomics.nuclear.ReactorPhysics;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Developer-only visual and formation check of the five recommended RBMK core diameters. */
public final class RbmkVariantsPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(RbmkVariantsPlaytestWorld.class);
    private static final String WORLD = "createplant-rbmk-variants-" + System.currentTimeMillis();
    private static final int GROUND_Y = -60, CORE_BOTTOM = 5, CORE_TOP = CORE_BOTTOM + ReactorPhysics.CORE_HEIGHT - 1;
    private static final int LOWER_BOTTOM = CORE_BOTTOM - ReactorPhysics.LOWER_STRUCTURE_HEIGHT;
    private static final int CAP_Y = CORE_TOP + ReactorPhysics.CAP_OFFSET;
    private static final int OUTLET_Y = CORE_TOP + ReactorPhysics.UPPER_SHIELD_HEIGHT + ReactorPhysics.SUSPENSION_HEIGHT / 2;
    private static final int FIRST_OUTER_X = -38, GAP = 3;
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static CompletableFuture<?> pending;
    private static int phase, ticks;

    private RbmkVariantsPlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (pending != null) {
                if (!pending.isDone()) return;
                pending.join();
                pending = null;
            }
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.fov().set(48);
                mc.options.renderDistance().set(12);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant RBMK variants test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(44L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                ticks = 0;
                pending = mc.getSingleplayerServer().submit(() -> prepare(mc));
            } else if (phase == 2 && ++ticks > 100) {
                Screenshot.grab(mc.gameDirectory, "rbmk-variants-perspective.png", mc.getMainRenderTarget(), message -> { });
                phase = 3;
                ticks = 0;
                pending = look(mc, 0, GROUND_Y + 72, 0, 0, GROUND_Y + CAP_Y, 0);
            } else if (phase == 3 && ++ticks > 80) {
                Screenshot.grab(mc.gameDirectory, "rbmk-variants-top.png", mc.getMainRenderTarget(), message -> { });
                phase = 4;
                pending = mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().saveEverything(false, true, true));
            } else if (phase == 4) {
                phase = -2;
                Files.writeString(mc.gameDirectory.toPath().resolve("rbmk-variants-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                LOG.info("RBMK VARIANTS PLAYTEST PASSED: {}", REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("RBMK VARIANTS PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (int x = FIRST_OUTER_X - 6; x <= -FIRST_OUTER_X + 6; x++)
            for (int z = -16; z <= 16; z++) put(level, new BlockPos(x, GROUND_Y, z), Blocks.SMOOTH_STONE.defaultBlockState());

        int outerX = FIRST_OUTER_X;
        List<Map<String, Object>> variants = new ArrayList<>();
        for (int size : ReactorCoreVariants.RECOMMENDED_DIAMETERS) {
            BlockPos origin = new BlockPos(outerX + ReactorCoreStructure.SHIELD_THICKNESS, GROUND_Y, -size / 2);
            variants.add(build(level, origin, size));
            outerX += ReactorCoreVariants.outerDiameter(size) + GAP;
        }
        REPORT.put("minimumCore", ReactorCoreStructure.MIN_WIDTH + "x" + ReactorCoreStructure.MIN_WIDTH);
        REPORT.put("shieldThickness", ReactorCoreStructure.SHIELD_THICKNESS);
        REPORT.put("variants", variants);

        var player = server.getPlayerList().getPlayers().getFirst();
        player.getAbilities().flying = true;
        player.getAbilities().mayfly = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        place(player, level, -4, GROUND_Y + 48, -84, 0, GROUND_Y + 13, 0);
        BlockPos spawn = new BlockPos(0, GROUND_Y + 1, -16);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 180, true, false);
        level.setDefaultSpawnPos(spawn, 180);
    }

    private static Map<String, Object> build(ServerLevel level, BlockPos origin, int size) {
        int shell = ReactorCoreStructure.SHIELD_THICKNESS;
        BlockState shield = CENuclear.BIOLOGICAL_SHIELD.get().defaultBlockState();
        for (int x = -shell; x < size + shell; x++) for (int z = -shell; z < size + shell; z++) {
            if (!ReactorCoreStructure.inCircle(x + shell, z + shell, ReactorCoreVariants.outerDiameter(size))) continue;
            boolean core = x >= 0 && x < size && z >= 0 && z < size && ReactorCoreStructure.inCircle(x, z, size);
            if (core) {
                for (int y = LOWER_BOTTOM; y < CORE_BOTTOM; y++) put(level, at(origin, x, y, z), CENuclear.FEED_BUNDLE.get().defaultBlockState());
                for (int y = CORE_BOTTOM; y <= CORE_TOP; y++) put(level, at(origin, x, y, z), CENuclear.FUEL_CHANNEL.get().defaultBlockState());
                for (int y = CORE_TOP + 1; y < CAP_Y; y++) put(level, at(origin, x, y, z), CENuclear.FUEL_CHANNEL_TUBE.get().defaultBlockState());
                put(level, at(origin, x, CAP_Y, z), CENuclear.TOP_CAP.get().defaultBlockState());
            } else for (int y = LOWER_BOTTOM; y <= CAP_Y; y++) put(level, at(origin, x, y, z), shield);
        }

        BlockPos inlet = at(origin, size / 2, LOWER_BOTTOM - 1, size / 2);
        BlockPos outlet = at(origin, size + shell - 1, OUTLET_Y, size / 2);
        BlockPos drive = at(origin, -shell, CORE_BOTTOM + 3, size / 2);
        put(level, inlet, AllBlocks.FLUID_PIPE.getDefaultState());
        put(level, inlet.below(), AllBlocks.FLUID_PIPE.getDefaultState());
        put(level, outlet, CENuclear.SHIELD_OUTLET.get().defaultBlockState());
        put(level, outlet.east(), AllBlocks.FLUID_PIPE.getDefaultState());
        put(level, drive, CENuclear.SHIELD_DRIVE.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        put(level, drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));

        BlockPos probe = at(origin, size / 2, CORE_BOTTOM, size / 2);
        var result = ReactorCoreStructure.scan(level, probe);
        require(result.formed() && result.circular() && result.width() == size,
            size + " m core did not form: " + result.reason() + " at " + result.fault());
        var lead = (FuelChannelBlockEntity) level.getBlockEntity(result.controller());
        lead.adopt(result, null);

        int installed = 0;
        for (BlockPos bottom : result.fuel()) {
            var column = (FuelChannelBlockEntity) level.getBlockEntity(bottom);
            var cap = (RodCapBlockEntity) level.getBlockEntity(bottom.above(ReactorPhysics.FUEL_ASSEMBLY_LENGTH));
            for (int slot = 0; slot < FuelChannelBlockEntity.RODS; slot++) if (column.active(slot) && column.control(slot)) {
                require(cap.installControl(slot), "Could not install control rod " + slot + " in " + size + " m variant");
                column.controlDepth(slot, ReactorPhysics.ROD_TRAVEL);
                installed++;
            }
        }
        lead.adopt(ReactorCoreStructure.scan(level, probe), null);
        ReactorCore reactor = lead.core();
        require(reactor != null && reactor.formed(), size + " m core was lost after loading control rods");
        var expected = ReactorCoreVariants.circular(size);
        require(result.fuel().size() == expected.bundleColumns()
            && reactor.fuelChannels() == expected.fuelChannels() && reactor.controlChannels() == expected.controlChannels(),
            size + " m channel counts changed from the advertised variant");
        reactor.tick();

        IFluidHandler water = level.getCapability(Capabilities.FluidHandler.BLOCK, inlet.above(), Direction.DOWN);
        int capacity = water == null ? 0 : water.getTankCapacity(0);
        require(capacity > 0 && water.fill(new FluidStack(Fluids.WATER, capacity), IFluidHandler.FluidAction.EXECUTE) == capacity,
            size + " m core did not accept its full coolant capacity");
        require(installed == reactor.controlChannels(), size + " m variant installed " + installed + " of " + reactor.controlChannels() + " control rods");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("coreDiameter", size);
        report.put("outsideDiameter", ReactorCoreVariants.outerDiameter(size));
        report.put("physicalBundleColumns", result.fuel().size());
        report.put("fineChannels", reactor.fuelChannels() + reactor.controlChannels());
        report.put("fuelChannels", reactor.fuelChannels());
        report.put("controlChannels", reactor.controlChannels());
        report.put("controlRodsInstalled", installed);
        report.put("coolantMb", capacity);
        report.put("nominalThermalPowerMw", reactor.fuelChannels());
        return report;
    }

    private static BlockPos at(BlockPos origin, int x, int y, int z) { return origin.offset(x, y, z); }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_ALL); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static CompletableFuture<?> look(Minecraft mc, double x, double y, double z, double tx, double ty, double tz) {
        return mc.getSingleplayerServer().submit(() -> place(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),
            mc.getSingleplayerServer().overworld(), x, y, z, tx, ty, tz));
    }

    private static void place(net.minecraft.server.level.ServerPlayer player, ServerLevel level, double x, double y, double z,
                              double tx, double ty, double tz) {
        float yaw = (float) Math.toDegrees(-Math.atan2(tx - x, tz - z));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx - x, tz - z)));
        player.teleportTo(level, x, y, z, yaw, pitch);
    }
}
