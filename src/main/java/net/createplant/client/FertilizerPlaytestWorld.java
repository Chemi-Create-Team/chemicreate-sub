package net.createplant.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.gametest.HaberBoschGameTests;
import net.createplant.gametest.ReactionTowerGameTests;
import net.createplant.gametest.ReactorGameTests;
import net.createplant.gametest.ThermalControlGameTests;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in creative flat world with a fertilizer plant built up from raw materials to NPK fertilizer:
 * felled wood is gasified for hydrogen, air is drawn through a nozzle, compressed and separated for nitrogen, water is electrolysed for oxygen
 * and more hydrogen; sulfur burns in that oxygen, its dioxide is absorbed into sulfurous acid and electrolysed to sulfuric acid, which with bone meal
 * gives phosphoric acid and with calcite phosphate fertilizer; hydrogen and nitrogen become synthesis gas, are compressed and turned to ammonia over
 * an iron catalyst and separated with the unreacted gas recycled; calcined calcite gives the carbon dioxide that turns the ammonia into
 * urea; dried kelp gives potash; and a Create mixer blends urea, phosphate fertilizer and potash into NPK fertilizer.
 * Every fluid moves through a Create pump on a stainless line and every item through a hopper, a basin or a chest.
 * {@code -Dcreateplant.fertilizerPlaytest} builds it, runs it until NPK fertilizer is made, checks the lines and saves the world for play.
 */
public final class FertilizerPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(FertilizerPlaytestWorld.class);
    private static final String TAG = "FERTILIZER";
    private static final String WORLD = "createplant-fertilizer-" + System.currentTimeMillis();
    private static final int TIMEOUT = 96_000, Y = -60;
    /** The plant stands this far east of the origin, clear of the battery plant when both share one world. */
    static final int OX = 70;
    // The front of the plant: wood to hydrogen.
    private static final BlockPos GASIFIER = new BlockPos(OX + 0, Y + 1, 0);
    // Air: a nozzle, a compressor and the air separation column.
    private static final BlockPos NOZZLE = new BlockPos(OX + 28, Y + 2, -4), AIR_CYLINDER = new BlockPos(OX + 32, Y + 3, -4), AIR_COLUMN = new BlockPos(OX + 38, Y, -12);
    // Water electrolysis for oxygen and hydrogen, and the sulfur cycle.
    private static final BlockPos WATER_CELL = new BlockPos(OX + 52, Y + 1, 4), SO2_VESSEL = new BlockPos(OX + 62, Y + 2, -2), SO2_TOWER = new BlockPos(OX + 65, Y + 2, -12);
    private static final BlockPos ACID_CELL = new BlockPos(OX + 76, Y + 1, -12), PHOSPHORIC = new BlockPos(OX + 86, Y + 2, -6);
    // Ammonia: synthesis gas, its compressor, the converter and the separator.
    private static final BlockPos SYNGAS_VESSEL = new BlockPos(OX + 98, Y + 2, -6), FEED_CYLINDER = new BlockPos(OX + 105, Y + 3, -10), CONVERTER = new BlockPos(OX + 110, Y + 2, -14);
    private static final BlockPos COOLER = new BlockPos(OX + 116, Y, -10), SEPARATOR = new BlockPos(OX + 126, Y + 2, -12);
    // Carbon dioxide, urea, phosphate fertilizer, potash and the NPK mixer.
    private static final BlockPos CALCINER = new BlockPos(OX + 110, Y + 3, 24), UREA = new BlockPos(OX + 118, Y + 3, 24), PHOSPHATE = new BlockPos(OX + 124, Y + 3, 30);
    private static final BlockPos NPK_BASIN = new BlockPos(OX + 120, Y + 1, 30), POTASH_BASIN = NPK_BASIN.south(), NPK_CHEST = NPK_BASIN.west();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<BlockPos[]> CELLS = new ArrayList<>();
    private static BlockPos COOLANT;
    private static PlaytestPlant plant;
    private static SyngasTrain syngas;
    private static volatile String failure;
    private static volatile boolean done;
    private static int phase, ticks;
    private static long serverTicks;

    private FertilizerPlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant fertilizer test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                    } catch (RuntimeException error) {
                        LOG.error(TAG + " build failed", error);
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                ticks = 0;
                view(mc, OX + 60, -30, 40, 200, 35);
            } else if (phase == 3 && ++ticks == 60) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "fertilizer-overview.png", mc.getMainRenderTarget(), message -> { });
                view(mc, OX + 112, -52, 36, 160, 30);
            } else if (phase == 3 && ticks == 120) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "fertilizer-npk.png", mc.getMainRenderTarget(), message -> { });
                view(mc, OX + 12.5, Y, 8.5, 150, 15);
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.saveEverything(false, true, true));
            } else if (phase == 3 && ticks > 160) {
                phase = -2;
                Files.writeString(mc.gameDirectory.toPath().resolve("fertilizer-playtest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                LOG.info(TAG + " PLAYTEST VERIFIED: {}", REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error(TAG + " PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.connection.teleport(x, y, z, yaw, pitch);
        });
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        addTo(level);
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        BlockPos spawn = new BlockPos(OX + 12, Y, 8);
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 150, 15);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 150, true, false);
        level.setDefaultSpawnPos(spawn, 150);
        REPORT.put("world", WORLD);
    }

    /** Builds the whole plant (from {@code OX} eastward) into an existing flat world and starts its server-side run; the world and the player are the caller's. */
    static void addTo(ServerLevel level) {
        plant = new PlaytestPlant(level, Y);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, FertilizerPlaytestWorld::serverTick);

        // Hydrogen from wood; its carbon monoxide is kept in a store.
        syngas = new SyngasTrain(plant, level, GASIFIER);
        BlockPos hydrogenCut = null;
        for (var cut : syngas.build()) {
            if (cut.fluid().isSame(CEFluids.HYDROGEN_GAS.get())) { hydrogenCut = cut.pipe(); continue; }
            var line = plant.route(Direction.NORTH, cut.pipe(), List.of(cut.pipe().north(2), cut.pipe().north(2).west(3)), Direction.WEST);
            plant.store(line.getLast(), Direction.WEST, "一酸化炭素", true);
        }

        // Nitrogen from air.
        put(level, NOZZLE, AllBlocks.NOZZLE.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP));
        plant.compressor(AIR_CYLINDER);
        BlockPos intake = NOZZLE.below();
        plant.route(Direction.DOWN, intake, List.of(intake.below(), intake.below().south(2), intake.below().south(2).atY(Y + 9),
            new BlockPos(AIR_CYLINDER.getX(), Y + 9, intake.getZ() + 2), new BlockPos(AIR_CYLINDER.getX(), Y + 9, AIR_CYLINDER.getZ()), AIR_CYLINDER.above()), Direction.DOWN);
        var air = plant.column(AIR_COLUMN, "air");
        BlockPos airInlet = air.getBlockPos().offset(2, 2, 0), discharge = AIR_CYLINDER.below();
        plant.route(Direction.DOWN, discharge, List.of(discharge.below(), discharge.below().south(3), discharge.below().south(3).atY(Y + 11),
            new BlockPos(airInlet.getX(), Y + 11, discharge.getZ() + 3), new BlockPos(airInlet.getX(), Y + 11, airInlet.getZ()), airInlet), Direction.WEST);
        BlockPos oxygenCut = air.getBlockPos().offset(0, 0, -1), argonCut = air.getBlockPos().offset(0, 9, -1), nitrogenCut = air.getBlockPos().offset(0, 13, -1);
        var liquidOxygen = plant.route(Direction.NORTH, oxygenCut, List.of(oxygenCut.north(2), oxygenCut.north(2).west(2)), Direction.WEST);
        plant.store(liquidOxygen.getLast(), Direction.WEST, "液体酸素", false);
        var argon = plant.route(Direction.NORTH, argonCut, List.of(argonCut.north(2), argonCut.north(2).east(3)), Direction.EAST);
        plant.store(argon.getLast(), Direction.EAST, "粗アルゴン", true);

        // Water electrolysis: hydrogen on the cathode side, oxygen on the anode side.
        cell(level, WATER_CELL);
        put(level, WATER_CELL.south(3), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.NORTH, WATER_CELL.south(2), List.of(WATER_CELL.south(2).below(), WATER_CELL.below()), Direction.UP);

        // Sulfur burns in oxygen to its dioxide in a heated stirred tank.
        var so2 = vessel(level, SO2_VESSEL, HeatCondition.HEATED);
        feeder(level, SO2_VESSEL.offset(3, 0, 1), Direction.WEST, BuiltInRegistries.ITEM.get(net.createplant.CreatePlant.location("sulfur_dust")), "硫黄");
        BlockPos anode = WATER_CELL.east();
        plant.route(Direction.EAST, anode, List.of(anode.east(), anode.east().atY(Y + 13), new BlockPos(SO2_VESSEL.getX() - 1, Y + 13, anode.getZ()),
            new BlockPos(SO2_VESSEL.getX() - 1, Y + 13, SO2_VESSEL.getZ() + 1), new BlockPos(SO2_VESSEL.getX() - 1, SO2_VESSEL.getY(), SO2_VESSEL.getZ() + 1)), Direction.EAST);

        // The dioxide into the bottom of an absorption tower with water on top; the sulfurous acid from its underside to the acid cell.
        AbsorptionTowerGameTests.tower(level, SO2_TOWER, 4, true);
        BlockPos so2Out = SO2_VESSEL.offset(1, 2, 1), towerIn = SO2_TOWER.west(2);
        plant.route(Direction.UP, so2Out, List.of(so2Out.above(), new BlockPos(so2Out.getX(), so2Out.getY() + 1, towerIn.getZ()), new BlockPos(towerIn.getX(), so2Out.getY() + 1, towerIn.getZ()),
            towerIn), Direction.EAST);
        BlockPos towerTop = SO2_TOWER.above(3);
        put(level, towerTop.above(4), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.DOWN, towerTop.above(3), List.of(towerTop.above()), Direction.DOWN);
        var offgas = plant.route(Direction.NORTH, towerTop.north(2), List.of(towerTop.north(4)), Direction.NORTH);
        plant.store(offgas.getLast(), Direction.NORTH, "未吸収ガス", true);
        cell(level, ACID_CELL);
        BlockPos under = SO2_TOWER.below();
        plant.route(Direction.DOWN, under, List.of(under.below(), new BlockPos(ACID_CELL.getX(), Y, under.getZ()), ACID_CELL.below()), Direction.UP);

        // Sulfuric acid, water and bone meal to phosphoric acid in a heated stirred tank; its gypsum to a chest.
        var phosphoric = vessel(level, PHOSPHORIC, HeatCondition.HEATED);
        feeder(level, PHOSPHORIC.offset(3, 0, 1), Direction.WEST, Items.BONE_MEAL, "骨粉");
        BlockPos acidEnd = ACID_CELL.south();
        plant.route(Direction.SOUTH, acidEnd, List.of(acidEnd.south(), acidEnd.south().atY(Y + 15), new BlockPos(PHOSPHORIC.getX() + 1, Y + 15, acidEnd.getZ() + 1),
            new BlockPos(PHOSPHORIC.getX() + 1, Y + 15, PHOSPHORIC.getZ() - 1), new BlockPos(PHOSPHORIC.getX() + 1, PHOSPHORIC.getY(), PHOSPHORIC.getZ() - 1)), Direction.SOUTH);
        BlockPos waterIn = PHOSPHORIC.offset(-1, 0, 1);
        put(level, waterIn.west(3).above(2), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.DOWN, waterIn.west(3).above(), List.of(waterIn.west(3), waterIn), Direction.EAST);
        output(level, PHOSPHORIC.offset(2, -1, 2), "石膏");

        // Phosphoric acid and calcite to phosphate fertilizer, which a hopper chain carries to the NPK basin.
        vessel(level, PHOSPHATE, HeatCondition.HEATED);
        feeder(level, PHOSPHATE.offset(3, 0, 1), Direction.WEST, Items.CALCITE, "方解石");
        BlockPos phosphoricOut = PHOSPHORIC.offset(1, 0, 3);
        plant.route(Direction.SOUTH, phosphoricOut, List.of(phosphoricOut.south(), phosphoricOut.south().atY(Y + 17), new BlockPos(PHOSPHATE.getX() + 1, Y + 17, phosphoricOut.getZ() + 1),
            new BlockPos(PHOSPHATE.getX() + 1, Y + 17, PHOSPHATE.getZ() - 1), new BlockPos(PHOSPHATE.getX() + 1, PHOSPHATE.getY(), PHOSPHATE.getZ() - 1)), Direction.SOUTH);
        hoppers(level, List.of(PHOSPHATE.offset(0, -1, 2), PHOSPHATE.offset(0, -2, 2), PHOSPHATE.offset(0, -2, 1), PHOSPHATE.offset(0, -2, 0)), NPK_BASIN);

        // Hydrogen from the gasifier and both cells, and nitrogen from the air column, to the synthesis gas tank.
        var synthesis = vessel(level, SYNGAS_VESSEL, HeatCondition.NONE);
        plant.route(Direction.NORTH, hydrogenCut, List.of(hydrogenCut.north(5), hydrogenCut.north(5).atY(Y + 19), new BlockPos(SYNGAS_VESSEL.getX() - 1, Y + 19, hydrogenCut.getZ() - 5),
            new BlockPos(SYNGAS_VESSEL.getX() - 1, Y + 19, SYNGAS_VESSEL.getZ()), new BlockPos(SYNGAS_VESSEL.getX() - 1, SYNGAS_VESSEL.getY(), SYNGAS_VESSEL.getZ())), Direction.EAST);
        BlockPos cathode = WATER_CELL.west();
        plant.route(Direction.WEST, cathode, List.of(cathode.west(), cathode.west().atY(Y + 21), new BlockPos(cathode.getX() - 1, Y + 21, SYNGAS_VESSEL.getZ() + 2),
            new BlockPos(SYNGAS_VESSEL.getX() - 1, Y + 21, SYNGAS_VESSEL.getZ() + 2), new BlockPos(SYNGAS_VESSEL.getX() - 1, SYNGAS_VESSEL.getY(), SYNGAS_VESSEL.getZ() + 2)), Direction.EAST);
        BlockPos acidCathode = ACID_CELL.west();
        plant.route(Direction.WEST, acidCathode, List.of(acidCathode.west(), acidCathode.west().atY(Y + 23), new BlockPos(acidCathode.getX() - 1, Y + 23, SYNGAS_VESSEL.getZ() + 3),
            new BlockPos(SYNGAS_VESSEL.getX() + 1, Y + 23, SYNGAS_VESSEL.getZ() + 3), new BlockPos(SYNGAS_VESSEL.getX() + 1, SYNGAS_VESSEL.getY(), SYNGAS_VESSEL.getZ() + 3)), Direction.NORTH);
        plant.route(Direction.NORTH, nitrogenCut, List.of(nitrogenCut.north(2), new BlockPos(SYNGAS_VESSEL.getX() + 3, nitrogenCut.getY(), nitrogenCut.getZ() - 2),
            new BlockPos(SYNGAS_VESSEL.getX() + 3, nitrogenCut.getY(), SYNGAS_VESSEL.getZ() + 1), new BlockPos(SYNGAS_VESSEL.getX() + 3, SYNGAS_VESSEL.getY(), SYNGAS_VESSEL.getZ() + 1)), Direction.WEST);

        // The synthesis gas, compressed, to the converter; its hot gas through a water-cooled exchanger to the separator, whose unreacted gas goes back.
        plant.compressor(FEED_CYLINDER, 256);
        BlockPos synOut = SYNGAS_VESSEL.offset(1, 2, 1);
        plant.route(Direction.UP, synOut, List.of(synOut.above(), new BlockPos(synOut.getX(), synOut.getY() + 1, FEED_CYLINDER.getZ()),
            new BlockPos(FEED_CYLINDER.getX(), synOut.getY() + 1, FEED_CYLINDER.getZ()), FEED_CYLINDER.above()), Direction.DOWN);
        // Six layers convert six batches at once; each pass makes only a little ammonia.
        var converter = ReactionTowerGameTests.tower(level, CONVERTER, 6, ItemStack.EMPTY);
        for (int i = 0; i < 6; i++) converter.insert(CEItems.IRON_CATALYST.toStack());
        plant.burner(converter.getBlockPos().offset(-1, -1, -1), BlazeBurnerBlock.HeatLevel.KINDLED);
        BlockPos feedIn = converter.getBlockPos().west(2), feedOut = FEED_CYLINDER.below();
        plant.route(Direction.DOWN, feedOut, List.of(feedOut.below(), new BlockPos(feedOut.getX(), feedOut.getY() - 1, feedIn.getZ()),
            new BlockPos(feedIn.getX(), feedOut.getY() - 1, feedIn.getZ()), feedIn), Direction.EAST);
        // The converter's product leaves a bottom side still hot, passes the tubes of a water-cooled exchanger and goes to the separator.
        var cooler = ThermalControlGameTests.build(level, COOLER, Direction.NORTH, 3);
        var along = cooler.getBlockState().getValue(net.createplant.block.ThermalMachineBlock.FACING);
        var members = cooler.members();
        int cells = cooler.exchanger().width * cooler.exchanger().width, top = cooler.exchanger().width - 1;
        BlockPos tubeIn = members.getFirst().relative(along.getOpposite()), tubeOut = members.get(members.size() - cells).relative(along);
        BlockPos shellIn = members.get(members.size() - cells + top).above(), shellOut = members.get(top).above();
        BlockPos hot = converter.getBlockPos().east(2);
        plant.route(Direction.EAST, hot, List.of(hot.east(), hot.east().atY(Y + 7), new BlockPos(hot.getX() + 1, Y + 7, tubeIn.getZ()),
            new BlockPos(tubeIn.getX(), Y + 7, tubeIn.getZ()), tubeIn), along);
        COOLANT = shellIn.above(3);
        put(level, COOLANT, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.DOWN, shellIn.above(2), List.of(shellIn), Direction.DOWN);
        var warm = plant.route(Direction.UP, shellOut, List.of(shellOut.above(2), shellOut.above(2).south(3), shellOut.south(3).atY(Y)), Direction.SOUTH);
        plant.store(warm.getLast(), Direction.SOUTH, "冷却水（蒸気）", PlaytestPlant.Kind.GAS);
        var separator = HaberBoschGameTests.separator(level, SEPARATOR);
        BlockPos coldIn = SEPARATOR.above().north();
        plant.route(along, tubeOut, List.of(tubeOut.relative(along, 2), tubeOut.relative(along, 2).atY(coldIn.getY()), new BlockPos(tubeOut.getX() + 2, coldIn.getY(), coldIn.getZ() - 1),
            new BlockPos(coldIn.getX(), coldIn.getY(), coldIn.getZ() - 1), coldIn), Direction.SOUTH);
        BlockPos recycle = SEPARATOR.above(2), recycleIn = converter.getBlockPos().north(2);
        plant.route(Direction.UP, recycle, List.of(recycle.above(), new BlockPos(recycle.getX(), recycle.getY() + 1, recycleIn.getZ()), new BlockPos(recycleIn.getX(), recycle.getY() + 1, recycleIn.getZ()),
            recycleIn), Direction.SOUTH);

        // Calcite calcined for carbon dioxide; with the liquid ammonia it gives urea, which a hopper chain carries to the NPK basin.
        vessel(level, CALCINER, HeatCondition.SUPERHEATED);
        feeder(level, CALCINER.offset(1, 0, -1), Direction.SOUTH, Items.CALCITE, "方解石");
        output(level, CALCINER.offset(2, -1, 2), "生石灰");
        vessel(level, UREA, HeatCondition.HEATED);
        BlockPos co2Out = CALCINER.offset(1, 2, 1), ureaGas = UREA.offset(1, 0, -1);
        plant.route(Direction.UP, co2Out, List.of(co2Out.above(), new BlockPos(co2Out.getX(), co2Out.getY() + 1, ureaGas.getZ()), new BlockPos(ureaGas.getX(), co2Out.getY() + 1, ureaGas.getZ()), ureaGas), Direction.SOUTH);
        BlockPos liquid = SEPARATOR.below(), ureaLiquid = UREA.offset(-1, 0, 1);
        plant.route(Direction.DOWN, liquid, List.of(liquid.west(), liquid.west().atY(Y + 9), new BlockPos(liquid.getX() - 1, Y + 9, ureaLiquid.getZ()), new BlockPos(ureaLiquid.getX(), Y + 9, ureaLiquid.getZ()),
            ureaLiquid), Direction.EAST);
        BlockPos ureaWater = UREA.offset(3, 0, 1);
        var ureaDrain = plant.route(Direction.EAST, ureaWater, List.of(ureaWater.east(2)), Direction.EAST);
        plant.store(ureaDrain.getLast(), Direction.EAST, "尿素の副生水", false);
        hoppers(level, List.of(UREA.offset(2, -1, 2), UREA.offset(2, -2, 2), UREA.offset(2, -2, 3), UREA.offset(2, -2, 4), UREA.offset(2, -2, 5)), NPK_BASIN);

        // Potash from dried kelp in a heated basin, which hands it to the NPK basin; the NPK goes to a chest.
        basin(level, POTASH_BASIN, true, Direction.NORTH);
        feeder(level, POTASH_BASIN.south(), Direction.NORTH, Items.DRIED_KELP, "乾燥昆布");
        put(level, NPK_BASIN.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        basin(level, NPK_BASIN, false, Direction.WEST);
        put(level, NPK_CHEST, Blocks.CHEST.defaultBlockState());

        plant.driveAll();
        plant.sign(NOZZLE.offset(-1, -2, 1), "空気取入口");
        plant.sign(AIR_CYLINDER.offset(0, -3, 2), "空気圧縮機");
        plant.sign(AIR_COLUMN.offset(-1, 0, 2), "空気分離塔");
        plant.sign(WATER_CELL.offset(-2, -1, 2), "水の電解（酸素・水素）");
        plant.sign(SO2_VESSEL.offset(-1, -2, 4), "硫黄の燃焼（SO2）");
        plant.sign(SO2_TOWER.offset(-2, -2, 3), "亜硫酸の吸収塔");
        plant.sign(ACID_CELL.offset(-2, -1, 2), "亜硫酸の電解（硫酸）");
        plant.sign(PHOSPHORIC.offset(-1, -2, 4), "リン酸");
        plant.sign(PHOSPHATE.offset(-1, -3, 4), "リン酸肥料");
        plant.sign(SYNGAS_VESSEL.offset(-1, -2, 4), "合成ガス");
        plant.sign(FEED_CYLINDER.offset(0, -3, 2), "合成ガス圧縮機");
        plant.sign(CONVERTER.offset(-1, -2, 4), "アンモニア合成塔");
        plant.sign(COOLER.offset(0, 0, 3), "冷却器");
        plant.sign(SEPARATOR.offset(1, -2, 1), "アンモニア分離器");
        plant.sign(CALCINER.offset(-1, -3, 4), "方解石の焼成（CO2）");
        plant.sign(UREA.offset(-1, -3, 4), "尿素");
        plant.sign(POTASH_BASIN.offset(1, -1, 1), "カリ灰");
        plant.sign(NPK_BASIN.offset(-1, -1, -1), "NPK肥料");
    }

    /** A 3x3 stainless tank two layers high over its stirrer, a cogwheel turning the stirrer from the north on a motor, and a burner for the heat. */
    private static ReactorMachineBlockEntity vessel(ServerLevel level, BlockPos base, HeatCondition heat) {
        var vessel = ReactorGameTests.assemble(level, base, ReactorModule.STIRRED, 2);
        BlockPos stirrer = base.offset(1, -1, 1), cog = stirrer.north();
        put(level, cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        plant.motor(cog.below(), Direction.UP);
        if (heat != HeatCondition.NONE)
            plant.burner(base.below(), heat == HeatCondition.SUPERHEATED ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED);
        return vessel;
    }

    /** A chest of raw material over a hopper that feeds it into the machine beside. */
    private static void feeder(ServerLevel level, BlockPos hopper, Direction into, Item item, String name) {
        put(level, hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, into));
        put(level, hopper.above(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, into.getOpposite()));
        var chest = (ChestBlockEntity) level.getBlockEntity(hopper.above());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(item, item.getDefaultMaxStackSize()));
        plant.sign(hopper.relative(into.getOpposite()), name);
    }

    /** A hopper under a tank corner that takes the solid product down into a chest. */
    private static void output(ServerLevel level, BlockPos hopper, String name) {
        put(level, hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        put(level, hopper.below(), Blocks.CHEST.defaultBlockState());
        plant.sign(hopper.below().south(), name);
    }

    /** A chain of hoppers, the first under a tank corner, each pointing at the next and the last into {@code target}. */
    private static void hoppers(ServerLevel level, List<BlockPos> chain, BlockPos target) {
        for (int i = 0; i < chain.size(); i++) {
            BlockPos next = i + 1 < chain.size() ? chain.get(i + 1) : target, at = chain.get(i);
            var facing = Direction.fromDelta(next.getX() - at.getX(), next.getY() - at.getY(), next.getZ() - at.getZ());
            PlaytestPlant.require(facing != null && facing != Direction.UP, "A hopper at " + at.toShortString() + " cannot point at " + next.toShortString());
            put(level, at, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
        }
    }

    /** A Create basin with a mixer above it turned by a motor; heated on a burner, handing its products to the block on {@code output}. */
    private static void basin(ServerLevel level, BlockPos at, boolean heated, Direction output) {
        if (heated) plant.burner(at.below(), BlazeBurnerBlock.HeatLevel.KINDLED);
        put(level, at, AllBlocks.BASIN.getDefaultState().setValue(BasinBlock.FACING, output));
        put(level, at.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        plant.motor(at.above(3), Direction.DOWN);
    }

    /** An electrolysis cell along Z with its head, a controller panel on the north, and a motor-driven alternator to the east. */
    private static void cell(ServerLevel level, BlockPos cell) {
        put(level, cell, CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        put(level, cell.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        for (int y = 0; y < 2; y++)
            put(level, cell.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        BlockPos brush = cell.offset(4, 1, -3), rotor = brush.east();
        plant.motor(brush.west(), Direction.EAST, 256);
        put(level, brush, CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST).setValue(AlternatorBrushesBlock.ROLL, true));
        put(level, rotor, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        for (var side : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN})
            put(level, rotor.relative(side), CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, side.getOpposite()).setValue(StatorBlock.ROLL, true));
        CELLS.add(new BlockPos[] {cell, brush});
    }

    /** Fits each cell's sixteen electrode plates, wires its alternator to the panel and presses START, as a player would. */
    private static void startCells(ServerLevel level) {
        var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
        var wiring = InfrastructureSavedData.load(level);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (BlockPos[] pair : CELLS) {
            BlockPos cell = pair[0], brush = pair[1], panel = cell.north().above();
            var rack = level.getCapability(Capabilities.ItemHandler.BLOCK, cell.above(), Direction.UP);
            PlaytestPlant.require(rack != null && rack.insertItem(1, new ItemStack(CEElectrolysis.NICKEL_ELECTRODE_PLATE.get(), 16), false).isEmpty(), "Sixteen nickel plates fill the head");
            var controller = (ElectrolysisControllerBlockEntity) level.getBlockEntity(cell.north());
            PlaytestPlant.require(controller.validate(), "The electrolysis cell forms: " + controller.status());
            for (int terminal = 0; terminal < 2; terminal++) wiring.connect(new InWorldNode(terminal, brush), new InWorldNode(terminal, panel), copper);
            level.getBlockState(panel).useWithoutItem(level, player, new BlockHitResult(Vec3.atLowerCornerOf(panel).add(.75, .21, 0), Direction.NORTH, panel, false));
            PlaytestPlant.require(controller.enabled(), "START enables the cell");
        }
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null) return;
        ServerLevel level = event.getServer().overworld();
        try {
            serverTicks++;
            if (serverTicks == 5 || serverTicks == 40) plant.correctDrives(syngas::wrongWay);
            if (serverTicks == 20) {
                syngas.start();
                plant.supply(WATER_CELL.south(3), new FluidStack(Fluids.WATER, 1_000));
                plant.supply(SO2_TOWER.above(7), new FluidStack(Fluids.WATER, 1_000));
                plant.supply(PHOSPHORIC.offset(-4, 2, 1), new FluidStack(Fluids.WATER, 1_000));
                plant.supply(COOLANT, new FluidStack(Fluids.WATER, 1_000));
                startCells(level);
            }
            if (serverTicks < 20) return;
            var converter = (ReactionTowerBlockEntity) level.getBlockEntity(CONVERTER.offset(1, 0, 1));
            var separator = (AmmoniaSeparatorBlockEntity) level.getBlockEntity(SEPARATOR);
            var basin = (BasinBlockEntity) level.getBlockEntity(NPK_BASIN);
            var air = (FractionatingColumnBlockEntity) level.getBlockEntity(AIR_COLUMN);
            mark("waterGasMade", !syngas.gasifierEntity().fluid(2).isEmpty());
            mark("airSeparating", air != null && air.status().equals("running"));
            mark("liquidAmmonia", separator != null && !separator.liquid().isEmpty());
            int npk = count(level, NPK_CHEST, CEItems.NPK_FERTILIZER.get()) + count(level, NPK_BASIN, CEItems.NPK_FERTILIZER.get());
            mark("npkFertilizer", npk > 0);
            if (serverTicks % 400 == 0) {
                LOG.info("{} tick {} air={} converter={} separatorFeed={} NH3={} npk={}", TAG, serverTicks, air == null ? "-" : air.status(), converter == null ? "-" : converter.status(),
                    separator == null ? "-" : separator.feed().getAmount(), separator == null ? 0 : separator.liquid().getAmount(), npk);
                plant.logLines(LOG, TAG);
                var liquidPort = level.getCapability(Capabilities.FluidHandler.BLOCK, SEPARATOR, Direction.DOWN);
                var ureaPort = level.getCapability(Capabilities.FluidHandler.BLOCK, UREA.offset(0, 0, 1), Direction.WEST);
                if (liquidPort != null && ureaPort != null && !liquidPort.getFluidInTank(0).isEmpty()) {
                    var sample = liquidPort.getFluidInTank(0).copy();
                    var path = new StringBuilder();
                    for (var pos : plant.LINES) if (pos.getX() >= SEPARATOR.getX() - 1 && pos.getZ() >= SEPARATOR.getZ() - 1 && pos.getY() < Y + 12) {
                        var stack = net.createplant.transport.PipeMaterial.contents(level.getBlockEntity(pos));
                        if (!stack.isEmpty()) path.append(pos.toShortString()).append('=').append(stack.getAmount()).append(' ');
                    }
                    var urea = (ReactorMachineBlockEntity) level.getBlockEntity(UREA);
                    LOG.info("{} ammonia {} urea accepts {} reactor port accepts {} storage {}", TAG, PlaytestPlant.describe(sample),
                        ureaPort.fill(sample, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE),
                        urea.port(Direction.WEST) == null ? "none" : urea.port(Direction.WEST).fill(sample, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE),
                        PlaytestPlant.describe(urea.displayedFluid(7)) + " / " + PlaytestPlant.describe(urea.displayedFluid(8)));
                }
                var shell = level.getCapability(Capabilities.FluidHandler.BLOCK, COOLER.above(), Direction.UP);
                if (shell != null) LOG.info("{} cooler shell outlet {}", TAG, PlaytestPlant.describe(shell.getFluidInTank(0)));
                for (var at : List.of(SO2_VESSEL, PHOSPHORIC, PHOSPHATE, SYNGAS_VESSEL, CALCINER, UREA)) {
                    var vessel = (ReactorMachineBlockEntity) level.getBlockEntity(at);
                    var items = level.getCapability(Capabilities.ItemHandler.BLOCK, at, Direction.UP);
                    var held = new StringBuilder();
                    if (items != null) for (int slot = 0; slot < items.getSlots(); slot++) if (!items.getStackInSlot(slot).isEmpty()) held.append(items.getStackInSlot(slot)).append(' ');
                    var fluids = new StringBuilder();
                    for (int slot = 0; slot < 8; slot++) if (!vessel.displayedFluid(slot).isEmpty()) fluids.append(slot).append(':').append(vessel.displayedFluid(slot).getAmount()).append(' ')
                        .append(BuiltInRegistries.FLUID.getKey(vessel.displayedFluid(slot).getFluid()).getPath()).append(' ');
                    LOG.info("{} vessel {} status={} formed={} items={} fluids={}", TAG, at.toShortString(), vessel.status(), vessel.formed(), held, fluids);
                }
                for (BlockPos[] pair : CELLS) {
                    var cell = (ElectrolysisControllerBlockEntity) level.getBlockEntity(pair[0].north());
                    LOG.info("{} cell {} status={} V={} feed={}", TAG, pair[0].toShortString(), cell.status(), Math.round(cell.volts()), cell.stored(0));
                }
                var tower = (net.createplant.block.entity.AbsorptionTowerBlockEntity) level.getBlockEntity(SO2_TOWER);
                LOG.info("{} SO2 tower status={}", TAG, tower == null ? "-" : tower.status());
                var gasifier = syngas.gasifierEntity();
                var syn = (ReactorMachineBlockEntity) level.getBlockEntity(SYNGAS_VESSEL);
                var nitrogen = new StringBuilder();
                for (var pos : plant.LINES) if (pos.getY() == Y + 13 && pos.getZ() < -13) {
                    var stack = net.createplant.transport.PipeMaterial.contents(level.getBlockEntity(pos));
                    if (!stack.isEmpty()) nitrogen.append(pos.getX()).append(' ');
                }
                LOG.info("{} gasifier={} coal={} gas={} synthesis tanks={} {} {} {} N2 pipes with gas at x={}", TAG, gasifier.status(), gasifier.itemCount(0), gasifier.fluid(2).getAmount(),
                    PlaytestPlant.describe(syn.displayedFluid(0)), PlaytestPlant.describe(syn.displayedFluid(1)), PlaytestPlant.describe(syn.displayedFluid(2)), syn.status(), nitrogen);
            }
            PlaytestPlant.require(serverTicks < TIMEOUT, "The plant stalled: " + REPORT);
            if (npk <= 0) return;
            plant.requireNoWear();
            REPORT.put("ticks", serverTicks);
            REPORT.put("npk", npk);
            REPORT.put("linesChecked", plant.LINES.size());
            done = true;
            tell(event, "肥料プラント：NPK肥料ができました（" + serverTicks + " tick、" + npk + "個）。");
        } catch (RuntimeException error) {
            LOG.error(TAG + " tick failed", error);
            failure = error.getMessage();
            tell(event, "肥料プラントが止まりました：" + failure + "（run/logs/latest.log の FERTILIZER を見てください）");
        }
    }

    private static void tell(ServerTickEvent.Post event, String message) {
        event.getServer().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    private static int count(ServerLevel level, BlockPos pos, Item item) {
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        int total = 0;
        if (handler != null) for (int slot = 0; slot < handler.getSlots(); slot++) if (handler.getStackInSlot(slot).is(item)) total += handler.getStackInSlot(slot).getCount();
        return total;
    }

    private static void mark(String key, boolean reached) {
        if (reached && !REPORT.containsKey(key)) {
            REPORT.put(key, serverTicks);
            LOG.info("{} {} at tick {}", TAG, key, serverTicks);
        }
    }

    private static void put(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { plant.put(pos, state); }
}
