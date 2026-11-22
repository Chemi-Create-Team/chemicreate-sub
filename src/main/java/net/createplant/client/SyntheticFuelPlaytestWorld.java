package net.createplant.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.gametest.DistillationGameTests;
import net.createplant.gametest.ReactionTowerGameTests;
import net.createplant.gametest.ThermalControlGameTests;
import net.createplant.registry.CEGasifier;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReciprocating;
import net.createplant.registry.CETransport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in creative flat world with the synthetic fuel line built from felled wood to a running diesel engine: logs from a chest are baked to
 * charcoal on a belt in a fan's blast past a blaze burner and drop through a chute into the gasifier roof; a Create boiler sends steam to the
 * gasifier; its water gas is split into carbon monoxide and hydrogen in a fractionating column, both are compressed in reciprocating compressors
 * and turned to synthetic crude over an iron Fischer-Tropsch catalyst; a second column cuts the crude, and its diesel runs a Create Diesel
 * Generators engine. Every transfer is a Create belt, chute or pump on a stainless line. {@code -Dcreateplant.syntheticFuelPlaytest} builds it,
 * runs it until the engine turns on the plant's own diesel, checks the line and saves the world for play.
 */
public final class SyntheticFuelPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(SyntheticFuelPlaytestWorld.class);
    private static final String WORLD = "createplant-synthetic-fuel-" + System.currentTimeMillis();
    private static final int BELT_RPM = 8, TIMEOUT = 48_000, ENGINE_TICKS = 600, Y = -60;
    private static final BlockPos GASIFIER = new BlockPos(0, Y + 1, 0), GASIFIER_ROOF = GASIFIER.above(5);
    private static final BlockPos BOILER = new BlockPos(-7, Y + 1, -1), WATER = new BlockPos(-10, Y + 1, 0);
    private static final BlockPos EXCHANGER = new BlockPos(3, Y, -6), WASHER = new BlockPos(11, Y + 2, -10);
    private static BlockPos COOLANT;
    private static final BlockPos SPLITTER = new BlockPos(-2, Y, -10), CO_CYLINDER = new BlockPos(-10, Y + 3, -16), H2_CYLINDER = new BlockPos(-2, Y + 3, -16);
    private static final BlockPos FT = new BlockPos(6, Y + 2, -24), CRUDE = new BlockPos(16, Y, -24), ENGINE = new BlockPos(26, Y + 1, -38);
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static PlaytestPlant plant;
    private static volatile String failure;
    private static volatile boolean done;
    private static int phase, ticks;
    private static long serverTicks, engineSince = -1;

    private SyntheticFuelPlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant synthetic fuel test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SyntheticFuelPlaytestWorld::serverTick);
                    } catch (RuntimeException error) {
                        LOG.error("SYNTHETIC FUEL build failed", error);
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                ticks = 0;
                view(mc, 26, -44, 18, 150, 30);
            } else if (phase == 3 && ++ticks == 60) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "synthetic-fuel-overview.png", mc.getMainRenderTarget(), message -> { });
                view(mc, 30, -54, -28, 120, 25);
            } else if (phase == 3 && ticks == 120) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory, "synthetic-fuel-engine.png", mc.getMainRenderTarget(), message -> { });
                BlockPos spawn = new BlockPos(12, Y, 8);
                view(mc, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 150, 15);
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.saveEverything(false, true, true));
            } else if (phase == 3 && ticks > 160) {
                phase = -2;
                Files.writeString(mc.gameDirectory.toPath().resolve("synthetic-fuel-playtest-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                LOG.info("SYNTHETIC FUEL PLAYTEST VERIFIED: {}", REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("SYNTHETIC FUEL PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    /** Moves the player to look over the plant, as a player standing there would. */
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
        plant = new PlaytestPlant(level, Y);
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);

        gasifier(level);
        charcoal(level);
        steam(level);
        var splitter = column(level, SPLITTER, "separate_purified_syngas");
        // The wet water gas from the front of the gasifier roof through the tubes of a water-cooled exchanger, where its steam condenses,
        // to the column's east inlet.
        var exchanger = ThermalControlGameTests.build(level, EXCHANGER, Direction.NORTH, 3);
        var along = exchanger.getBlockState().getValue(net.createplant.block.ThermalMachineBlock.FACING);
        var members = exchanger.members();
        int cells = exchanger.exchanger().width * exchanger.exchanger().width, top = exchanger.exchanger().width - 1;
        BlockPos tubeIn = members.getFirst().relative(along.getOpposite()), tubeOut = members.get(members.size() - cells).relative(along);
        BlockPos shellIn = members.get(members.size() - cells + top).above(), shellOut = members.get(top).above();
        BlockPos outlet = GASIFIER_ROOF.north();
        int gasRack = outlet.getY() + 3;
        plant.route(Direction.UP, outlet.above(), List.of(outlet.above(3), new BlockPos(outlet.getX(), gasRack, tubeIn.getZ()),
            new BlockPos(outlet.getX(), tubeIn.getY(), tubeIn.getZ()), tubeIn), along);
        plant.put(shellIn.above(3), AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.DOWN, shellIn.above(2), List.of(shellIn), Direction.DOWN);
        var warm = plant.route(Direction.UP, shellOut, List.of(shellOut.above(2), shellOut.above(2).south(3), shellOut.south(3).atY(Y)), Direction.SOUTH);
        plant.store(warm.getLast(), Direction.SOUTH, "冷却水（温水）", PlaytestPlant.Kind.ANY);
        // The cooled stream is washed in an absorption tower: the condensate and wash water leave its underside, the clean syngas its top storey.
        AbsorptionTowerGameTests.tower(level, WASHER, 4, true);
        BlockPos washIn = WASHER.west(2), washTop = WASHER.above(3).north(2), washUnder = WASHER.below();
        BlockPos clear = tubeOut.relative(along, 2);
        plant.route(along, tubeOut, List.of(clear, clear.atY(washIn.getY()), new BlockPos(clear.getX(), washIn.getY(), washIn.getZ()), washIn), Direction.EAST);
        BlockPos inlet = inlet(splitter, "separate_purified_syngas");
        int cooledRack = Y + 7;
        plant.route(Direction.NORTH, washTop, List.of(washTop.north(), washTop.north().atY(cooledRack), new BlockPos(washTop.getX(), cooledRack, inlet.getZ()),
            new BlockPos(inlet.getX(), cooledRack, inlet.getZ()), inlet), Direction.WEST);
        var drain = plant.route(Direction.DOWN, washUnder, List.of(washUnder.below(), washUnder.below().east(3)), Direction.EAST);
        plant.store(drain.getLast(), Direction.EAST, "洗浄塔の排水", PlaytestPlant.Kind.ANY);
        COOLANT = shellIn.above(3);

        compressor(level, CO_CYLINDER);
        compressor(level, H2_CYLINDER);
        var ft = ReactionTowerGameTests.tower(level, FT, 3, ItemStack.EMPTY);
        for (int i = 0; i < 3; i++) ft.insert(CEItems.IRON_FT_CATALYST_CORE.toStack());
        plant.burner(ft.getBlockPos().offset(-1, -1, -1), BlazeBurnerBlock.HeatLevel.KINDLED);
        plant.burner(ft.getBlockPos().offset(1, -1, 1), BlazeBurnerBlock.HeatLevel.KINDLED);

        // The column's cuts: carbon monoxide and hydrogen each to their own compressor; half the monoxide, more than the iron catalyst can use, to a store.
        var split = cuts(splitter, "separate_purified_syngas");
        for (var cut : split) {
            boolean monoxide = cut.fluid().isSame(CEProcessFluids.CARBON_MONOXIDE.get());
            BlockPos cylinder = monoxide ? CO_CYLINDER : H2_CYLINDER;
            // The lower outlet reaches one block further north so the two risers stand apart.
            int lower = cut.pipe().getY() == split.stream().mapToInt(other -> other.pipe().getY()).min().orElseThrow() ? 1 : 0;
            BlockPos riser = cut.pipe().north(1 + lower);
            int rack = Y + 9 + 2 * (1 - lower);
            var corner = new BlockPos(cylinder.getX(), rack, riser.getZ());
            var path = plant.route(Direction.NORTH, cut.pipe(), List.of(riser, riser.atY(rack), corner,
                new BlockPos(cylinder.getX(), rack, cylinder.getZ()), cylinder.above()), Direction.DOWN);
            if (monoxide) plant.store(corner, corner.getX() < riser.getX() ? Direction.WEST : Direction.EAST, "一酸化炭素の余剰", true);
            REPORT.put(monoxide ? "monoxideCut" : "hydrogenCut", cut.pipe().toShortString());
        }
        // The compressed gases into two outer sides of the tower's bottom layer.
        BlockPos coInlet = ft.getBlockPos().west(2), h2Inlet = ft.getBlockPos().south(2);
        plant.route(Direction.DOWN, CO_CYLINDER.below(), List.of(CO_CYLINDER.below(2), new BlockPos(CO_CYLINDER.getX(), Y + 1, coInlet.getZ()),
            new BlockPos(coInlet.getX(), Y + 1, coInlet.getZ()), coInlet), Direction.EAST);
        plant.route(Direction.DOWN, H2_CYLINDER.below(), List.of(H2_CYLINDER.below(2), new BlockPos(H2_CYLINDER.getX(), Y, H2_CYLINDER.getZ()),
            new BlockPos(H2_CYLINDER.getX(), Y, h2Inlet.getZ() + 2), new BlockPos(h2Inlet.getX(), Y, h2Inlet.getZ() + 2), new BlockPos(h2Inlet.getX(), Y + 2, h2Inlet.getZ() + 2),
            new BlockPos(h2Inlet.getX(), Y + 2, h2Inlet.getZ()), h2Inlet), Direction.NORTH);
        // The FT off-gas from the top layer's east side to a store.
        BlockPos offgas = ft.getBlockPos().offset(2, 2, 0);
        var offgasLine = plant.route(Direction.EAST, offgas, List.of(offgas.east(2), offgas.east(2).above(2)), Direction.UP);
        plant.store(offgasLine.getLast(), Direction.UP, "FTオフガス", true);

        // The crude from under the tower to the second column, whose cuts go to stores and the diesel to the engine.
        var crude = column(level, CRUDE, "distill_synthetic_crude");
        BlockPos crudeInlet = inlet(crude, "distill_synthetic_crude");
        BlockPos under = ft.getBlockPos().below();
        int crudeZ = ft.getBlockPos().getZ() + 2;
        plant.route(Direction.DOWN, under, List.of(under.below(), new BlockPos(under.getX(), Y, crudeZ),
            new BlockPos(crudeInlet.getX(), Y, crudeZ), new BlockPos(crudeInlet.getX(), Y, crudeInlet.getZ()), crudeInlet), Direction.WEST);
        engine(level);
        int store = 0, index = 0;
        for (var cut : cuts(crude, "distill_synthetic_crude")) {
            // The cuts leave the same face one above another, so their pumps stand at different distances to keep their cogs apart.
            int pumpAt = 1 + 2 * index++;
            String id = BuiltInRegistries.FLUID.getKey(cut.fluid()).getPath();
            if (cut.fluid().isSame(CEProcessFluids.LOW_SULFUR_DIESEL.get())) {
                // A horizontal engine takes its fuel from below.
                BlockPos fuel = ENGINE.below(), turn = cut.pipe().north(9);
                plant.route(Direction.NORTH, cut.pipe(), List.of(turn, turn.atY(fuel.getY()), new BlockPos(turn.getX(), fuel.getY(), fuel.getZ()), fuel), Direction.UP, pumpAt);
                REPORT.put("dieselCut", cut.pipe().toShortString());
            } else {
                BlockPos end = cut.pipe().north(8).west(2 + 2 * store++);
                var line = plant.route(Direction.NORTH, cut.pipe(), List.of(cut.pipe().north(8), new BlockPos(end.getX(), cut.pipe().getY(), end.getZ())), Direction.WEST, pumpAt);
                plant.store(line.getLast(), Direction.WEST, label(id), cut.fluid().isSame(CEProcessFluids.FT_OFFGAS.get()));
            }
        }

        plant.driveAll();
        plant.sign(new BlockPos(9, Y, 4), "原木→木炭");
        plant.sign(GASIFIER.offset(-2, -1, 2), "ガス化炉");
        plant.sign(BOILER.offset(0, -1, 3), "ボイラー（蒸気）");
        plant.sign(SPLITTER.offset(-1, 0, 2), "水性ガス分留");
        plant.sign(CO_CYLINDER.offset(0, -3, 2), "CO圧縮機");
        plant.sign(H2_CYLINDER.offset(0, -3, 2), "H2圧縮機");
        plant.sign(FT.offset(-1, -2, 4), "FT反応塔");
        plant.sign(CRUDE.offset(-1, 0, 2), "合成原油分留");
        plant.sign(ENGINE.offset(0, -1, 2), "ディーゼルエンジン");
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        BlockPos spawn = new BlockPos(12, Y, 8);
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 150, 15);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 150, true, false);
        level.setDefaultSpawnPos(spawn, 150);
        REPORT.put("world", WORLD);
    }

    private static String label(String id) {
        return switch (id) {
            case "ft_offgas" -> "軽質ガス";
            case "synthetic_naphtha" -> "合成ナフサ";
            case "molten_paraffin" -> "溶融パラフィン";
            default -> id;
        };
    }

    private static void gasifier(ServerLevel level) {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            plant.put(GASIFIER.offset(x, y, z), CEGasifier.GASIFIER.get().defaultBlockState());
        plant.burner(GASIFIER.below(), BlazeBurnerBlock.HeatLevel.KINDLED);
        PlaytestPlant.require(((GasifierBlockEntity) level.getBlockEntity(GASIFIER)).rebuild(), "The gasifier forms");
    }

    /** Felled logs in a chest drop through a hopper onto a belt, which carries them through a fan's blast past a lit blaze burner and into a chute on the roof. */
    private static void charcoal(ServerLevel level) {
        int beltY = GASIFIER_ROOF.getY() + 1, z = GASIFIER_ROOF.getZ() + 1;
        BlockPos chute = new BlockPos(GASIFIER_ROOF.getX() + 1, beltY, z), start = new BlockPos(9, beltY, z), end = chute.east();
        plant.put(chute, AllBlocks.CHUTE.getDefaultState());
        plant.put(start, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        plant.put(end, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        BeltConnectorItem.createBelts(level, start, end);
        // A slow belt keeps each log long enough in the blast to bake through.
        plant.motor(start.north(), Direction.SOUTH, BELT_RPM);
        plant.DRIVES.add(new BlockPos[] {start, start.north()});
        // A row of fans blows south across the belt, each through a lit burner.
        for (int x = end.getX() + 1; x < start.getX(); x++) {
            BlockPos fanAt = new BlockPos(x, beltY, z - 2);
            plant.put(fanAt, AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
            plant.burner(fanAt.south(), BlazeBurnerBlock.HeatLevel.KINDLED);
            plant.motor(fanAt.north(), Direction.SOUTH);
        }
        plant.put(start.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        plant.put(start.above(2), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        var chest = (ChestBlockEntity) level.getBlockEntity(start.above(2));
        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        REPORT.put("logs", chest.getContainerSize() * 64);
    }

    /** A 2x2 Create tank on four burners is the boiler; a pump fills it from the water supply and one on its roof sends steam to the gasifier's west inlet. */
    private static void steam(ServerLevel level) {
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            plant.put(BOILER.offset(x, 0, z), AllBlocks.FLUID_TANK.getDefaultState());
            plant.burner(BOILER.offset(x, -1, z), BlazeBurnerBlock.HeatLevel.KINDLED);
        }
        com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti((FluidTankBlockEntity) level.getBlockEntity(BOILER));
        plant.put(WATER, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState());
        plant.route(Direction.EAST, WATER.east(), List.of(WATER.east().above(2), new BlockPos(BOILER.getX(), WATER.getY() + 2, WATER.getZ()), new BlockPos(BOILER.getX(), BOILER.getY() + 1, WATER.getZ())), Direction.DOWN);
        BlockPos roof = BOILER.offset(1, 1, 0), inlet = GASIFIER.west(2);
        plant.route(Direction.UP, roof, List.of(roof.above(2), new BlockPos(roof.getX(), roof.getY() + 2, -3), new BlockPos(inlet.getX(), roof.getY() + 2, -3),
            new BlockPos(inlet.getX(), roof.getY() + 2, inlet.getZ()), inlet), Direction.EAST);
    }

    private static FractionatingColumnBlockEntity column(ServerLevel level, BlockPos origin, String recipe) {
        var column = DistillationGameTests.build(level, origin, 2, DistillationProfile.requiredHeight(recipe));
        PlaytestPlant.require(column.ready(), "The column for " + recipe + " forms");
        return column;
    }

    /** The pipe position east of the column's feed layer. */
    private static BlockPos inlet(FractionatingColumnBlockEntity column, String recipe) {
        int layer = DistillationProfile.feedLayer(DistillationProfile.requiredHeight(recipe));
        return column.getBlockPos().offset(2, layer, 0);
    }

    private record Cut(Fluid fluid, BlockPos pipe) { }

    /** Each cut's pipe position north of its outlet layer. */
    private static List<Cut> cuts(FractionatingColumnBlockEntity column, String recipe) {
        var fraction = DistillationProfile.fraction(recipe);
        int height = DistillationProfile.requiredHeight(recipe), count = fraction.outputs().size();
        var cuts = new ArrayList<Cut>();
        for (int i = 0; i < count; i++)
            cuts.add(new Cut(fraction.outputs().get(i).getFluid(), column.getBlockPos().offset(0, DistillationProfile.fractionLayer(i + 1, height, count), -1)));
        return cuts;
    }

    /** A cylinder with its engine on its east side, the crank shaft beyond it and a motor turning the shaft. */
    private static void compressor(ServerLevel level, BlockPos cylinder) {
        plant.put(cylinder, CEReciprocating.CYLINDER.get().defaultBlockState());
        var state = CEReciprocating.COMPRESSOR.get().defaultBlockState().setValue(ReciprocatingCompressorBlock.FACE, AttachFace.WALL).setValue(ReciprocatingCompressorBlock.FACING, Direction.EAST);
        BlockPos enginePos = cylinder.east();
        plant.put(enginePos, state);
        BlockPos shaft = ReciprocatingCompressorBlock.shaftPos(state, enginePos);
        plant.put(shaft, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, ReciprocatingCompressorBlock.shaftAxis(state)));
        var engine = (ReciprocatingCompressorBlockEntity) level.getBlockEntity(enginePos);
        engine.attachShaft();
        Direction along = Direction.get(Direction.AxisDirection.NEGATIVE, ReciprocatingCompressorBlock.shaftAxis(state));
        plant.motor(shaft.relative(along), along.getOpposite());
        for (int y = Y; y < cylinder.getY(); y++) if (level.getBlockState(new BlockPos(enginePos.getX(), y, enginePos.getZ())).isAir())
            plant.put(new BlockPos(enginePos.getX(), y, enginePos.getZ()), Blocks.SMOOTH_STONE.defaultBlockState());
    }

    private static void engine(ServerLevel level) {
        plant.put(ENGINE, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "diesel_engine")).defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.EAST));
        plant.put(ENGINE.east(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        plant.put(ENGINE.east(2), AllBlocks.SPEEDOMETER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.UP)
            .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        plant.put(ENGINE.east(3), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        plant.put(ENGINE.east(4), AllBlocks.ENCASED_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null) return;
        ServerLevel level = event.getServer().overworld();
        try {
            serverTicks++;
            // A cog or belt train sets each drive's turning direction; flip any that runs backwards.
            if (serverTicks == 5 || serverTicks == 40) plant.correctDrives((pos, speed) -> wrongWay(level, pos, speed));
            if (serverTicks == 20) {
                plant.supply(WATER, new FluidStack(Fluids.WATER, 1_000));
                plant.supply(COOLANT, new FluidStack(Fluids.WATER, 1_000));
                ((GasifierBlockEntity) level.getBlockEntity(GASIFIER)).rebuild();
            }
            if (serverTicks < 20) return;
            var gasifier = (GasifierBlockEntity) level.getBlockEntity(GASIFIER);
            var ft = (ReactionTowerBlockEntity) level.getBlockEntity(FT.offset(1, 0, 1));
            PlaytestPlant.require(gasifier != null && ft != null, "The generated world was left before the checks finished");
            mark("charcoalInGasifier", gasifier.itemCount(0) > 0);
            mark("steamInGasifier", !gasifier.fluid(1).isEmpty());
            mark("waterGasMade", !gasifier.fluid(2).isEmpty());
            int co = plant.amount(CO_CYLINDER, Direction.DOWN, null), h2 = plant.amount(H2_CYLINDER, Direction.DOWN, null);
            mark("monoxideCompressed", co > 0);
            mark("hydrogenCompressed", h2 > 0);
            int crude = 0;
            for (int i = 0; i < 4; i++) if (ft.tank(i).getFluid().isSame(CEProcessFluids.SYNTHETIC_CRUDE_OIL.get())) crude += ft.tank(i).getAmount();
            mark("crudeMade", crude > 0 || REPORT.containsKey("dieselAtEngine"));
            var engine = level.getBlockEntity(ENGINE);
            float speed = level.getBlockEntity(ENGINE.east()) instanceof KineticBlockEntity shaft ? Math.abs(shaft.getSpeed()) : 0;
            int fuel = plant.amount(ENGINE, Direction.DOWN, null);
            mark("dieselAtEngine", fuel > 0);
            if (speed > 0) { mark("engineTurning", true); if (engineSince < 0) engineSince = serverTicks; }
            if (serverTicks % 400 == 0) plant.logLines(LOG, "SYNTHETIC FUEL");
            if (serverTicks % 400 == 0)
                LOG.info("SYNTHETIC FUEL tick {} gasifier={} coal={} steam={} gas={} ft={} crude={} CO'={} H2'={} engineFuel={} rpm={}", serverTicks, gasifier.status(),
                    gasifier.itemCount(0), gasifier.fluid(1).getAmount(), gasifier.fluid(2).getAmount(), ft.status(), crude, co, h2, fuel, speed);
            PlaytestPlant.require(serverTicks < TIMEOUT, "The line stalled: " + REPORT + ", gasifier " + gasifier.status() + ", tower " + ft.status());
            if (engineSince < 0 || serverTicks - engineSince < ENGINE_TICKS) return;
            PlaytestPlant.require(speed > 0, "The engine keeps turning on the plant's diesel");
            plant.requireNoWear();
            REPORT.put("ticks", serverTicks);
            REPORT.put("engineRpm", speed);
            REPORT.put("engineFuelMb", fuel);
            REPORT.put("gasifierStatus", gasifier.status());
            REPORT.put("towerStatus", ft.status());
            REPORT.put("linesChecked", plant.LINES.size());
            done = true;
        } catch (RuntimeException error) {
            LOG.error("SYNTHETIC FUEL tick failed", error);
            failure = error.getMessage();
        }
    }

    /** A pump must push toward its front and a belt carry toward the gasifier. */
    private static boolean wrongWay(ServerLevel level, BlockPos pos, float speed) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.simibubi.create.content.kinetics.belt.BeltBlock)
            return com.simibubi.create.content.kinetics.belt.BeltHelper.getSegmentBE(level, pos) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt
                && belt.getMovementFacing() != Direction.WEST;
        return speed < 0;
    }

    private static void mark(String key, boolean reached) {
        if (reached && !REPORT.containsKey(key)) {
            REPORT.put(key, serverTicks);
            LOG.info("SYNTHETIC FUEL {} at tick {}", key, serverTicks);
        }
    }
}
