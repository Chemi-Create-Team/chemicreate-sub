package net.createplant.client;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.HorizontalFluidTankBlock;
import net.createplant.block.TurbineUnit;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.gametest.DistillationGameTests;
import net.createplant.gametest.ReactionTowerGameTests;
import net.createplant.gametest.ReactorGameTests;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEOstwaldFluids;
import net.createplant.registry.CEProcessBlocks;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.registry.CEWorldFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Opt-in flat-world playtest. Machines process only on their own server ticks. */
@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT)
public final class SteamAcidPlaytestWorld {
    private static final boolean ENABLED = Boolean.getBoolean("createplant.steamAcidPlaytest");
    private static final Logger LOG = LoggerFactory.getLogger(SteamAcidPlaytestWorld.class);
    private static final String WORLD = "steam-acid-flat-" + System.currentTimeMillis();
    private static final int Y = -59;
    private static final BlockPos BRUSH = at(22, 5, 10), OUTBOARD_BRUSH = BRUSH.east(2);
    private static final BlockPos BOILER = at(16, 5, 3), WATER = at(16, 5, 0);
    private static final BlockPos CONDENSATE = at(3, 2, 18);
    private static final BlockPos BURNER_VESSEL = at(5, 3, 23), CHLORINE_VESSEL = at(25, 3, 23);
    private static final BlockPos SULFUR_ABSORBER = at(6, 2, 32), CHLORINE_ABSORBER = at(26, 2, 32), NITRIC_ABSORBER = at(45, 2, 32);
    private static final BlockPos SULFUR_CELL = at(27, 3, 13), BRINE_CELL = at(32, 3, 13);
    private static final BlockPos NITRIC_REACTOR = at(39, 3, 22), FAN = at(44, 6, 23), COLUMN = at(49, 3, 22);
    private static final BlockPos SULFUR_PRODUCT = at(27, 3, 18), CHLORINE_PRODUCT = at(31, 2, 32), NITRIC_PRODUCT = at(50, 2, 32);
    private static final List<SteamTurbineBlockEntity> HP = new ArrayList<>(), LP = new ArrayList<>();
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();
    private static ElectrolysisControllerBlockEntity sulfurCell, brineCell;
    private static ReactionTowerBlockEntity nitricReactor;
    private static FractionatingColumnBlockEntity column;
    private static long start;
    private static int clientPhase, age, sulfurStep, chlorineStep, nitricStep;
    private static double maxVolts, maxWatts;
    private static boolean primed;
    private static volatile boolean done;
    private static volatile Throwable failure;

    private SteamAcidPlaytestWorld() { }
    private static BlockPos at(int x, int y, int z) { return new BlockPos(x, Y + y, z); }

    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (clientPhase == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++age >= 40) {
            clientPhase = 1;
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("蒸気発電と三酸製造", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                    .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        } else if (clientPhase == 1 && mc.player != null && mc.level != null && mc.getOverlay() == null) {
            clientPhase = 2;
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.getToasts().clear();
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                    NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SteamAcidPlaytestWorld::serverTick);
                } catch (RuntimeException error) {
                    failure = error;
                }
            });
        } else if (clientPhase == 2 && (done || failure != null)) {
            clientPhase = 3;
            try {
                if (failure != null) RESULTS.put("error", failure.toString());
                Files.writeString(mc.gameDirectory.toPath().resolve("steam-acid-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
            } catch (java.io.IOException error) {
                LOG.error("Could not write steam-acid results", error);
            }
            if (failure != null) LOG.error("STEAM ACID PLAYTEST FAILED", failure);
            else LOG.info("STEAM ACID PLAYTEST PASSED: {}", RESULTS);
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (int x = 0; x <= 53; x++) for (int z = -2; z <= 37; z++)
            put(level, at(x, 0, z), (x % 8 == 0 || z == 19 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE).defaultBlockState());

        for (int x = 3; x <= 12; x++) LP.add(turbine(level, at(x, 5, 10), TurbineUnit.Stage.LOW));
        for (int x = 17; x <= 21; x++) HP.add(turbine(level, at(x, 5, 10), TurbineUnit.Stage.HIGH));
        for (int x = 13; x <= 16; x++) put(level, at(x,5,10), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));

        var boilerState = CETransport.HORIZONTAL_FLUID_TANK.get().defaultBlockState().setValue(HorizontalFluidTankBlock.AXIS, Direction.Axis.X);
        for (int i = 0; i < 3; i++) {
            put(level, BOILER.east(i), boilerState);
            burner(level, BOILER.east(i).below());
            support(level, BOILER.east(i).below(2));
        }
        for (int x : List.of(17, 21)) for (int z : List.of(8, 12)) support(level, at(x, 2, z));
        for (int i = 0; i < 3; i++) put(level, WATER.above(i), AllBlocks.FLUID_TANK.getDefaultState());
        ConnectivityHandler.formMulti(entity(level, WATER, FluidTankBlockEntity.class));
        tank(level, WATER).fill(stack(Fluids.WATER, 24000), EXECUTE);
        line(level, AllBlocks.FLUID_PIPE.getDefaultState(), AllBlocks.MECHANICAL_PUMP.getDefaultState(), WATER, BOILER, WATER.south(2), Direction.SOUTH, 64);
        line(level, stainless(), stainlessPump(), BOILER.east(2), at(18, 6, 10), at(18, 7, 6), Direction.SOUTH, 64,
            at(18, 7, 3), at(18, 7, 10));
        line(level, stainless(), stainlessPump(), at(19, 5, 11), at(8, 5, 14), at(14, 5, 15), Direction.WEST, 64,
            at(19, 5, 15), at(8, 5, 15));
        put(level, CONDENSATE, AllBlocks.FLUID_TANK.getDefaultState());
        line(level, stainless(), stainlessPump(), at(3, 1, 10), CONDENSATE, at(3, 0, 16), Direction.SOUTH, 64,
            at(3, 0, 10), at(3, 0, 18));

        brush(level, BRUSH, Direction.WEST);
        var rotor = BRUSH.east();
        put(level, rotor, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        // One stator keeps the alternator within the steam set's shaft capacity.
        put(level, rotor.below(), CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, Direction.UP).setValue(StatorBlock.ROLL, true));
        support(level, rotor.below(2));
        brush(level, OUTBOARD_BRUSH, Direction.EAST);
        sulfurCell = cell(level, SULFUR_CELL, false);
        brineCell = cell(level, BRINE_CELL, true);

        ReactorGameTests.assemble(level, BURNER_VESSEL, ReactorModule.STIRRED, 1);
        ReactorGameTests.assemble(level, CHLORINE_VESSEL, ReactorModule.STIRRED, 1);
        motor(level, BURNER_VESSEL.offset(1,-1,1), Direction.UP, 64);
        motor(level, CHLORINE_VESSEL.offset(1,-1,1), Direction.UP, 64);
        burner(level, BURNER_VESSEL.below());
        var air = BURNER_VESSEL.offset(1, 0, 0);
        line(level, CETransport.STAINLESS_PIPE.get().defaultBlockState(), pump("stainless_steel_pump"),
            air.north(5), air, air.north(2), Direction.SOUTH, 64);

        for (var at : List.of(SULFUR_ABSORBER, CHLORINE_ABSORBER, NITRIC_ABSORBER)) AbsorptionTowerGameTests.tower(level, at, 4, true);
        nitricReactor = ReactionTowerGameTests.tower(level, NITRIC_REACTOR, 3, CEItems.OXIDATION_CATALYST.toStack());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 4; y++)
            put(level, FAN.offset(x,y-3,z), (y < 3 ? CEProcessBlocks.COOLER_RADIATOR.get()
                : x == 0 && z == 0 ? CEProcessBlocks.COOLING_FAN.get() : CEProcessBlocks.COOLING_FAN_SHROUD.get()).defaultBlockState());
        motor(level, FAN.below(4), Direction.UP, 64);
        column = DistillationGameTests.build(level, COLUMN, 2, 2);

        for (var at : List.of(SULFUR_PRODUCT, CHLORINE_PRODUCT, NITRIC_PRODUCT)) put(level, at, CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState());
        line(level, stainless(), stainlessPump(), SULFUR_CELL, SULFUR_PRODUCT, SULFUR_CELL.south(3), Direction.SOUTH, 64);
        line(level, stainless(), stainlessPump(), CHLORINE_ABSORBER.east(), CHLORINE_PRODUCT, CHLORINE_ABSORBER.east(4), Direction.EAST, 64);
        line(level, stainless(), stainlessPump(), NITRIC_ABSORBER.east(), NITRIC_PRODUCT, NITRIC_ABSORBER.east(4), Direction.EAST, 64);
        supplies(level);
        sign(level, at(20,1,20), "補給チェスト");
        sign(level, at(27,1,19), "硫酸");
        sign(level, at(31,1,33), "塩酸");
        sign(level, at(50,1,33), "硝酸");
        player.setItemSlot(EquipmentSlot.HEAD, com.simibubi.create.AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, com.simibubi.create.AllItems.WRENCH.asStack());
        player.connection.teleport(22.5, Y + 1, 20.5, 165, -4);
        level.setDefaultSpawnPos(at(22,1,20), 165);
        start = level.getGameTime();
        RESULTS.put("world", WORLD);
        LOG.info("STEAM ACID PLAYTEST assembled");
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null || event.getServer().isDedicatedServer()) return;
        var level = event.getServer().overworld();
        long ticks = level.getGameTime() - start;
        double volts = Math.max(sulfurCell.volts(), brineCell.volts());
        maxVolts = Math.max(maxVolts, volts);
        maxWatts = Math.max(maxWatts, sulfurCell.watts() + brineCell.watts());
        if (ticks % 5 != 0 || ticks < 20) return;
        try {
            if (!primed) {
                prime(level);
                primed = true;
            }
            sulfur(level, ticks);
            hydrochloric(level, ticks);
            nitric(level, ticks);
            if (ticks % 100 == 0) LOG.info("ACID tick {} stages {}/{}/{} water feed={} steam={} HP={} LP={} electrolysis={} V sulfur={} brine={}",
                ticks, sulfurStep, chlorineStep, nitricStep, tank(level, WATER).getFluidAmount(),
                entity(level, BOILER.east(2), HorizontalTankBlockEntity.class).steam().getFluidAmount(), work(HP), work(LP), volts,
                sulfurCell.status(), brineCell.status());
            if (sulfurStep == 3 && chlorineStep == 3 && nitricStep == 5 && work(LP) > 0) finish(level, ticks);
            if (ticks >= 2400 && !done) throw new IllegalStateException("Plant stalled at stages " + sulfurStep + "/" + chlorineStep + "/" + nitricStep);
        } catch (RuntimeException error) {
            failure = error;
        }
    }

    private static void prime(ServerLevel level) {
        var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
        var wiring = InfrastructureSavedData.load(level);
        for (var panel : List.of(sulfurCell, brineCell)) {
            require(panel.validate(), "Electrolysis panel did not form at " + panel.getBlockPos());
            var brush = panel == sulfurCell ? BRUSH : OUTBOARD_BRUSH;
            for (int terminal = 0; terminal < 2; terminal++)
                wiring.connect(new InWorldNode(terminal, brush), new InWorldNode(terminal, panel.getBlockPos().above()), copper);
            startPanel(level, panel.getBlockPos().above());
        }
        feed(port(level, BRINE_CELL, Direction.DOWN), stack(CEChlorAlkali.BRINE.get(), 500));
        var feed = itemPort(level, BURNER_VESSEL.offset(2,0,1), Direction.EAST);
        require(feed.insertItem(0, CEItems.SULFUR_DUST.toStack(), false).isEmpty(), "Sulfur powder was rejected");
        var nitric = ReactionTowerGameTests.port(nitricReactor);
        feed(nitric, stack(CEFluids.AMMONIA_GAS.get(), 300));
        feed(nitric, stack(CEFluids.OXYGEN_GAS.get(), 375));
        feed(port(level, SULFUR_ABSORBER.above(3), Direction.UP), stack(Fluids.WATER, 500));
        feed(port(level, CHLORINE_ABSORBER.above(3), Direction.UP), stack(Fluids.WATER, 500));
        feed(port(level, NITRIC_ABSORBER.above(3), Direction.UP), stack(Fluids.WATER, 100));
    }

    private static void sulfur(ServerLevel level, long ticks) {
        if (sulfurStep == 0 && move(port(level, BURNER_VESSEL, Direction.UP), port(level, SULFUR_ABSORBER.west(), Direction.WEST),
            CEUranium.SULFUR_DIOXIDE.get(), 500)) {
            RESULTS.put("sulfur_dioxide_tick", ticks);
            sulfurStep = 1;
        }
        if (sulfurStep == 1 && move(port(level, SULFUR_ABSORBER.west(), Direction.WEST), port(level, SULFUR_CELL, Direction.DOWN),
            CEUranium.SULFUROUS_ACID.get(), 500)) {
            RESULTS.put("sulfurous_acid_tick", ticks);
            sulfurStep = 2;
        }
        if (sulfurStep == 2 && tank(level, SULFUR_PRODUCT).getFluidAmount() >= 250) {
            RESULTS.put("sulfuric_acid_tick", ticks);
            sulfurStep = 3;
        }
    }

    private static void hydrochloric(ServerLevel level, long ticks) {
        var vessel = port(level, CHLORINE_VESSEL.offset(2,0,1), Direction.EAST);
        if (chlorineStep == 0 && brineCell.stored(1) >= 250 && brineCell.stored(2) >= 250) {
            require(move(port(level, BRINE_CELL, Direction.WEST), vessel, CEFluids.HYDROGEN_GAS.get(), 250), "Hydrogen did not transfer");
            require(move(port(level, BRINE_CELL, Direction.EAST), vessel, CEChlorAlkali.CHLORINE.get(), 250), "Chlorine did not transfer");
            RESULTS.put("brine_electrolysis_tick", ticks);
            chlorineStep = 1;
        }
        if (chlorineStep == 1 && move(port(level, CHLORINE_VESSEL, Direction.UP), port(level, CHLORINE_ABSORBER.west(), Direction.WEST),
            CEChlorAlkali.HYDROGEN_CHLORIDE.get(), 500)) {
            RESULTS.put("hydrogen_chloride_tick", ticks);
            chlorineStep = 2;
        }
        if (chlorineStep == 2 && tank(level, CHLORINE_PRODUCT).getFluidAmount() >= 1000) {
            RESULTS.put("hydrochloric_acid_tick", ticks);
            chlorineStep = 3;
        }
    }

    private static void nitric(ServerLevel level, long ticks) {
        var reaction = ReactionTowerGameTests.port(nitricReactor);
        if (nitricStep == 0 && move(reaction, port(level, FAN.below(2).north(), Direction.NORTH), net.createplant.thermal.FluidForm.HOT_NITRIC_OXIDE_MIXTURE, 750)) {
            RESULTS.put("ammonia_oxidation_tick", ticks);
            nitricStep = 1;
        }
        if (nitricStep == 1 && move(port(level, FAN.below(2).south(), Direction.SOUTH), DistillationGameTests.inlet(column, 1),
            CEProcessFluids.COLD_NITRIC_OXIDE_MIXTURE.get(), 750)) {
            RESULTS.put("nitric_mixture_cooling_tick", ticks);
            nitricStep = 2;
        }
        if (nitricStep == 2 && move(DistillationGameTests.outlet(column, 1), reaction, CEOstwaldFluids.NITRIC_OXIDE.get(), 300)) {
            feed(reaction, stack(CEFluids.OXYGEN_GAS.get(), 150));
            RESULTS.put("nitric_oxide_separation_tick", ticks);
            nitricStep = 3;
        }
        if (nitricStep == 3 && move(reaction, port(level, NITRIC_ABSORBER.west(), Direction.WEST), CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 300)) {
            RESULTS.put("nitrogen_dioxide_tick", ticks);
            nitricStep = 4;
        }
        if (nitricStep == 4 && tank(level, NITRIC_PRODUCT).getFluidAmount() >= 200) {
            RESULTS.put("nitric_acid_tick", ticks);
            nitricStep = 5;
        }
    }

    private static void finish(ServerLevel level, long ticks) {
        require(sulfurCell.consumed() > 0 && brineCell.consumed() > 0 && maxVolts > 0 && maxWatts > 0, "Both cells must receive actual electricity");
        require(work(HP) > 0 && work(LP) > 0, "Both steam stages must do work");
        var rpm = Math.abs(entity(level, BRUSH, KineticBlockEntity.class).getSpeed());
        require(rpm > 0, "The alternator shaft must be turning");
        require(tank(level, CONDENSATE).getFluidAmount() > 0, "LP condensate must reach its tank");
        require(tank(level,SULFUR_PRODUCT).getFluid().is(CEUranium.SULFURIC_ACID.get())
            && tank(level,CHLORINE_PRODUCT).getFluid().is(CEChlorAlkali.HYDROCHLORIC_ACID.get())
            && tank(level,NITRIC_PRODUCT).getFluid().is(CEFluids.NITRIC_ACID.get()), "Products must be the three specified acids");
        RESULTS.put("ticks", ticks);
        RESULTS.put("hp_work_J", work(HP));
        RESULTS.put("lp_work_J", work(LP));
        RESULTS.put("alternator_rpm", rpm);
        RESULTS.put("electrolysis_peak_V", maxVolts);
        RESULTS.put("electrolysis_peak_W", maxWatts);
        RESULTS.put("sulfuric_electric_J", sulfurCell.consumed());
        RESULTS.put("brine_electric_J", brineCell.consumed());
        RESULTS.put("condensate_mb", tank(level, CONDENSATE).getFluidAmount());
        RESULTS.put("sulfuric_mb", tank(level, SULFUR_PRODUCT).getFluidAmount());
        RESULTS.put("hydrochloric_mb", tank(level, CHLORINE_PRODUCT).getFluidAmount());
        RESULTS.put("nitric_mb", tank(level, NITRIC_PRODUCT).getFluidAmount());
        level.getServer().saveEverything(false, true, true);
        done = true;
    }

    private static SteamTurbineBlockEntity turbine(ServerLevel level, BlockPos pos, TurbineUnit.Stage stage) {
        require(TurbineUnit.place(level, new TurbineUnit.Layout(pos, Direction.Axis.X, stage), null, ItemStack.EMPTY), "Turbine placement refused at " + pos);
        return entity(level, pos, SteamTurbineBlockEntity.class);
    }

    private static void burner(ServerLevel level, BlockPos pos) {
        put(level, pos, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        var player = FakePlayerFactory.getMinecraft(level);
        var fuel = Items.COAL_BLOCK.getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, fuel);
        level.getBlockState(pos).useItemOn(fuel, level, player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(level.getBlockState(pos).getValue(BlazeBurnerBlock.HEAT_LEVEL).isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED),
            "Burner did not accept coal at " + pos);
    }

    private static void brush(ServerLevel level, BlockPos pos, Direction facing) {
        put(level, pos, CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, facing)
            .setValue(AlternatorBrushesBlock.ROLL, true));
    }

    private static ElectrolysisControllerBlockEntity cell(ServerLevel level, BlockPos pos, boolean brine) {
        put(level, pos, CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        put(level, pos.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        for (int y = 0; y < 2; y++)
            put(level, pos.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        var rack = itemPort(level, pos.above(), Direction.UP);
        require(rack.insertItem(1, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(), false).isEmpty(), "Plate was rejected");
        if (brine) require(rack.insertItem(2, CEItems.ASBESTOS.toStack(), false).isEmpty(), "Diaphragm was rejected");
        return entity(level, pos.north(), ElectrolysisControllerBlockEntity.class);
    }

    private static void startPanel(ServerLevel level, BlockPos pos) {
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(pos).useWithoutItem(level, player,
            new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.75, .21, 0), Direction.NORTH, pos, false));
        require(entity(level, pos.below(), ElectrolysisControllerBlockEntity.class).enabled(), "START did not enable " + pos);
    }

    private static void motor(ServerLevel level, BlockPos pos, Direction facing, int rpm) {
        put(level, pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        entity(level, pos, CreativeMotorBlockEntity.class).generatedSpeed.setValue(rpm);
    }

    /** Waypoints form an axis-aligned Create pipe path. The endpoints are existing machine faces. */
    private static void line(ServerLevel level, BlockState pipe, BlockState pump, BlockPos from, BlockPos to,
        BlockPos drive, Direction direction, int rpm, BlockPos... corners) {
        var route = new ArrayList<BlockPos>();
        route.add(from);
        var legs = new ArrayList<>(List.of(corners));
        legs.add(to);
        for (var corner : legs) {
            var cursor = route.getLast();
            var diff = corner.subtract(cursor);
            var face = Direction.fromDelta(Integer.signum(diff.getX()), Integer.signum(diff.getY()), Integer.signum(diff.getZ()));
            require(diff.equals(BlockPos.ZERO) || face != null, "Pipe route needs a corner between " + cursor + " and " + corner);
            while (!cursor.equals(corner)) {
                cursor = cursor.relative(face);
                route.add(cursor);
            }
        }
        require(route.contains(drive), "Pump is off route");
        for (int i = 1; i < route.size() - 1; i++) {
            var pos = route.get(i);
            if (pos.equals(drive)) {
                put(level, pos, pump.setValue(BlockStateProperties.FACING, direction));
            } else {
                var prev = route.get(i-1).subtract(pos);
                var next = route.get(i+1).subtract(pos);
                var state = pipe;
                for (Direction face : Direction.values()) {
                    var normal = face.getNormal();
                    state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(face), normal.equals(prev) || normal.equals(next));
                }
                level.setBlock(pos, state, 2);
            }
        }
        put(level, drive.above(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, direction.getAxis()));
        motor(level, drive.above().relative(direction.getOpposite()), direction, rpm);
    }

    private static void supplies(ServerLevel level) {
        var pos = at(21,1,20);
        put(level, pos, Blocks.CHEST.defaultBlockState());
        var chest = entity(level, pos, ChestBlockEntity.class);
        var items = List.of(Items.COAL_BLOCK.getDefaultInstance().copyWithCount(32), Items.WATER_BUCKET.getDefaultInstance(),
            CEItems.SULFUR_DUST.toStack(8), CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(4), CEItems.ASBESTOS.toStack(4),
            CEItems.OXIDATION_CATALYST.toStack(), CEElectrolysis.CELL.toStack(4), CEReactor.STIRRER.toStack(2),
            CETransport.STAINLESS_PIPE.toStack(64), stainlessPump().getBlock().asItem().getDefaultInstance().copyWithCount(12),
            CEWorldFluids.bucket(stack(CEChlorAlkali.BRINE.get(),1000)), CEItems.AMMONIA_GAS_BUCKET.toStack(), CEItems.OXYGEN_GAS_BUCKET.toStack(),
            CEItems.OXIDATION_CATALYST.toStack(), CEItems.OXIDATION_CATALYST.toStack());
        for (int i = 0; i < items.size(); i++) chest.setItem(i, items.get(i));
    }

    private static void support(ServerLevel level, BlockPos top) {
        for (var pos = top; pos.getY() > Y; pos = pos.below()) put(level, pos, Blocks.POLISHED_ANDESITE.defaultBlockState());
    }

    private static void sign(ServerLevel level, BlockPos pos, String label) {
        put(level, pos, Blocks.OAK_SIGN.defaultBlockState());
        var sign = entity(level, pos, SignBlockEntity.class);
        sign.setText(sign.getFrontText().setMessage(0, Component.literal(label)), true);
    }

    private static double work(List<SteamTurbineBlockEntity> rotors) {
        return rotors.stream().mapToDouble(be -> be.thermodynamics().totalWork()).sum();
    }

    private static FluidTank tank(ServerLevel level, BlockPos pos) {
        var be = entity(level, pos, FluidTankBlockEntity.class);
        return (FluidTank) be.getControllerBE().getTankInventory();
    }

    private static void feed(IFluidHandler port, FluidStack stack) {
        require(port.fill(stack, SIMULATE) == stack.getAmount() && port.fill(stack, EXECUTE) == stack.getAmount(), "Incomplete feed " + stack);
    }

    /** One measured transfer between exposed ports, acting as the operator carrying an intermediate to the next station. */
    private static boolean move(IFluidHandler source, IFluidHandler target, Fluid fluid, int amount) {
        return move(source, target, stack(fluid, amount), stored -> stored.is(fluid), amount);
    }

    private static boolean move(IFluidHandler source, IFluidHandler target, net.createplant.thermal.FluidForm form, int amount) {
        return move(source, target, form.stack(amount), form::matches, amount);
    }

    private static boolean move(IFluidHandler source, IFluidHandler target, FluidStack request, java.util.function.Predicate<FluidStack> wanted, int amount) {
        var sample = source.drain(request, SIMULATE);
        if (sample.isEmpty()) {
            for (int slot = 0; slot < source.getTanks(); slot++) {
                var stored = source.getFluidInTank(slot);
                if (wanted.test(stored)) {
                    sample = source.drain(stored.copyWithAmount(amount), SIMULATE);
                    if (!sample.isEmpty()) break;
                }
            }
        }
        if (sample.getAmount() < amount) return false;
        require(target.fill(sample, SIMULATE) == amount, "Destination rejected " + sample);
        var drained = source.drain(sample, EXECUTE);
        require(drained.getAmount() == amount, "Incomplete withdrawal " + drained);
        feed(target, drained);
        return true;
    }

    private static IFluidHandler port(ServerLevel level, BlockPos pos, Direction face) {
        var port = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        require(port != null, "No fluid port at " + pos + " on " + face);
        return port;
    }

    private static IItemHandler itemPort(ServerLevel level, BlockPos pos, Direction face) {
        var port = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        require(port != null, "No item port at " + pos + " on " + face);
        return port;
    }

    private static BlockState stainless() { return CETransport.STAINLESS_PIPE.get().defaultBlockState(); }
    private static BlockState stainlessPump() { return pump("stainless_steel_pump"); }
    private static BlockState pump(String path) { return BuiltInRegistries.BLOCK.get(CreatePlant.location(path)).defaultBlockState(); }
    private static FluidStack stack(Fluid fluid, int amount) { return new FluidStack(fluid, amount); }
    private static void put(ServerLevel level, BlockPos pos, BlockState state) { level.setBlockAndUpdate(pos, state); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static <T> T entity(ServerLevel level, BlockPos pos, Class<T> type) {
        var be = level.getBlockEntity(pos);
        require(type.isInstance(be), "No " + type.getSimpleName() + " at " + pos + " (" + level.getBlockState(pos) + ")");
        return type.cast(be);
    }
}
