package net.createplant.client;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.gametest.AbsorptionTowerGameTests;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEUranium;
import net.createplant.transport.ContainmentWear;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluid;
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
 * Opt-in creative flat world with the hybrid sulfur cycle piped end to end: creative tanks feed sulfur dioxide into the bottom of a
 * four-storey absorption tower and water into its top, the sulfurous acid runs from its underside to an electrolysis cell powered by a
 * motor-driven alternator, and the cell's hydrogen and sulfuric acid run to their tanks. Every transfer is a Create pump on a stainless line.
 * {@code -Dcreateplant.sulfurCyclePlaytest} builds it, runs it until sulfuric acid collects, checks the lines and saves the world for play.
 */
public final class SulfurCyclePlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(SulfurCyclePlaytestWorld.class);
    private static final String WORLD = "createplant-sulfur-cycle-" + System.currentTimeMillis();
    private static final int RPM = 256, PRODUCT_TARGET = 1_000, TIMEOUT = 12_000, GROUND = -60;
    private static final BlockPos TOWER = new BlockPos(0, -58, 0), TOP = TOWER.above(3);
    private static final BlockPos SO2_SUPPLY = TOWER.west(5), WATER_SUPPLY = TOP.above(3), OFF_GAS = TOP.north(4);
    private static final BlockPos CELL = TOWER.south(8), PANEL = CELL.north().above(), HYDROGEN = CELL.west(3), ACID = CELL.south(3);
    private static final BlockPos BRUSH = CELL.offset(4, 1, -3), ROTOR = BRUSH.east();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<BlockPos[]> PUMPS = new ArrayList<>();
    private static final List<BlockPos> LINES = new ArrayList<>();
    private static volatile String failure;
    private static volatile boolean done;
    private static int phase, ticks;
    private static long serverTicks;
    private static double peakVolts;

    private SulfurCyclePlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant sulfur cycle test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, SulfurCyclePlaytestWorld::serverTick);
                    } catch (RuntimeException error) {
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                ticks = 0;
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.saveEverything(false, true, true));
            } else if (phase == 3 && ++ticks > 40) {
                phase = -2;
                Files.writeString(mc.gameDirectory.toPath().resolve("sulfur-cycle-playtest-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                LOG.info("SULFUR CYCLE PLAYTEST VERIFIED: {}", REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("SULFUR CYCLE PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        AbsorptionTowerGameTests.tower(level, TOWER, 4, true);

        // Sulfur dioxide into a straight side of the bottom storey; the diagonal corners take no pipe.
        tank(level, SO2_SUPPLY);
        line(level, SO2_SUPPLY.east(), Direction.EAST, Direction.WEST);
        pump(level, SO2_SUPPLY.east(2), Direction.EAST, Direction.UP, Direction.WEST);
        line(level, SO2_SUPPLY.east(3), Direction.EAST, Direction.WEST);
        // Water onto the top of the top storey's centre.
        tank(level, WATER_SUPPLY);
        pump(level, WATER_SUPPLY.below(), Direction.DOWN, Direction.NORTH, Direction.UP);
        line(level, WATER_SUPPLY.below(2), Direction.UP, Direction.DOWN);
        // Unabsorbed gas leaves a straight side of the top storey.
        line(level, TOP.north(2), Direction.NORTH, Direction.SOUTH);
        pump(level, TOP.north(3), Direction.NORTH, Direction.UP, Direction.NORTH);
        put(level, OFF_GAS, CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState());

        // Sulfurous acid from the tower's underside to the cell's input below it.
        BlockPos under = TOWER.below();
        line(level, under, Direction.UP, Direction.SOUTH);
        for (int z = 1; z < 8; z++)
            if (z == 4) pump(level, under.south(z), Direction.SOUTH, Direction.UP, Direction.SOUTH);
            else line(level, under.south(z), Direction.NORTH, Direction.SOUTH);
        line(level, under.south(8), Direction.NORTH, Direction.UP);

        put(level, CELL, CEElectrolysis.CELL.get().defaultBlockState().setValue(ElectrolysisCellBlock.AXIS, Direction.Axis.Z));
        put(level, CELL.above(), CEElectrolysis.HEAD.get().defaultBlockState().setValue(ElectrodeHeadBlock.AXIS, Direction.Axis.Z));
        for (int y = 0; y < 2; y++)
            put(level, CELL.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState().setValue(ElectrolysisControllerBlock.FACING, Direction.NORTH));
        // Hydrogen leaves the cathode side, sulfuric acid the far end.
        line(level, CELL.west(), Direction.EAST, Direction.WEST);
        pump(level, CELL.west(2), Direction.WEST, Direction.UP, Direction.WEST);
        put(level, HYDROGEN, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X));
        line(level, CELL.south(), Direction.NORTH, Direction.SOUTH);
        pump(level, CELL.south(2), Direction.SOUTH, Direction.UP, Direction.SOUTH);
        put(level, ACID, CETransport.STAINLESS_STEEL_TANK.get().defaultBlockState());

        // A creative motor turns the alternator; four stators around the rotor.
        motor(level, BRUSH.west(), Direction.EAST);
        put(level, BRUSH, CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST).setValue(AlternatorBrushesBlock.ROLL, true));
        put(level, ROTOR, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        for (var side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN})
            put(level, ROTOR.relative(side), CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, side.getOpposite()).setValue(StatorBlock.ROLL, true));

        sign(level, new BlockPos(-5, GROUND, -2), "二酸化硫黄");
        sign(level, new BlockPos(-3, GROUND, -3), "吸収塔");
        sign(level, new BlockPos(2, GROUND, 7), "電解セル");
        sign(level, new BlockPos(-3, GROUND, 10), "水素");
        sign(level, new BlockPos(1, GROUND, 12), "硫酸");
        sign(level, new BlockPos(1, GROUND, -4), "未吸収ガス");
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        player.getInventory().setItem(1, AllItems.WRENCH.asStack());
        BlockPos spawn = new BlockPos(7, GROUND, 14);
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 135, 15);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 135, true, false);
        level.setDefaultSpawnPos(spawn, 135);
        REPORT.put("world", WORLD);
        REPORT.put("tower", TOWER.toShortString());
        REPORT.put("cell", CELL.toShortString());
    }

    private static void serverTick(ServerTickEvent.Post event) {
        if (done || failure != null) return;
        ServerLevel level = event.getServer().overworld();
        try {
            serverTicks++;
            // A cog train sets each pump's turning direction; a positive speed moves fluid toward its front.
            if (serverTicks == 5 || serverTicks == 40)
                for (BlockPos[] pair : PUMPS)
                    if (((KineticBlockEntity) level.getBlockEntity(pair[0])).getSpeed() < 0 && level.getBlockEntity(pair[1]) instanceof CreativeMotorBlockEntity motor)
                        motor.generatedSpeed.setValue(-motor.generatedSpeed.getValue());
            if (serverTicks == 20) start(level);
            if (serverTicks < 20) return;
            var tower = (AbsorptionTowerBlockEntity) level.getBlockEntity(TOWER);
            var cell = (ElectrolysisControllerBlockEntity) level.getBlockEntity(CELL.north());
            require(tower != null && cell != null, "The generated world was left before the checks finished");
            peakVolts = Math.max(peakVolts, cell.volts());
            int sulfurous = amount(level, TOWER, Direction.DOWN, CEUranium.SULFUROUS_ACID.get());
            if (sulfurous > 0) REPORT.putIfAbsent("sulfurousAcidAtTick", serverTicks);
            if (cell.stored(0) > 0) REPORT.putIfAbsent("cellFedAtTick", serverTicks);
            int hydrogen = amount(level, HYDROGEN, Direction.EAST, CEFluids.HYDROGEN_GAS.get());
            int acid = amount(level, ACID, Direction.NORTH, CEUranium.SULFURIC_ACID.get());
            if (hydrogen > 0) REPORT.putIfAbsent("hydrogenAtTick", serverTicks);
            if (acid > 0) REPORT.putIfAbsent("sulfuricAcidAtTick", serverTicks);
            if (serverTicks % 200 == 0)
                LOG.info("SULFUR CYCLE tick {} tower={} cell={} V={} in={} sulfurous={} H2={} acid={}", serverTicks, tower.status(), cell.status(),
                    String.format(Locale.ROOT, "%.0f", cell.volts()), cell.stored(0), sulfurous, hydrogen, acid);
            require(serverTicks < TIMEOUT, "The cycle stalled: tower " + tower.status() + ", cell " + cell.status() + ", " + cell.volts() + " V, sulfurous "
                + sulfurous + ", cell input " + cell.stored(0) + ", hydrogen " + hydrogen + ", sulfuric " + acid);
            if (acid < PRODUCT_TARGET) return;
            for (var pos : LINES) {
                var wear = level.getBlockEntity(pos) instanceof SmartBlockEntity smart ? smart.getBehaviour(ContainmentWear.TYPE) : null;
                require(wear == null || wear.wear() == 0, "The line at " + pos.toShortString() + " wears: " + wear.reason());
            }
            require(tower.formed(), "The tower stays formed");
            require(hydrogen > 0, "Hydrogen reaches its receiver");
            REPORT.put("ticks", serverTicks);
            REPORT.put("sulfuricAcidMb", acid);
            REPORT.put("hydrogenMb", hydrogen);
            REPORT.put("offGasMb", amount(level, OFF_GAS, Direction.SOUTH, CEUranium.SULFUR_DIOXIDE.get()));
            REPORT.put("cellPeakVolts", peakVolts);
            REPORT.put("cellElectricJ", cell.consumed());
            REPORT.put("alternatorRpm", Math.abs(((KineticBlockEntity) level.getBlockEntity(ROTOR)).getSpeed()));
            REPORT.put("towerStatus", tower.status());
            REPORT.put("cellStatus", cell.status());
            REPORT.put("linesChecked", LINES.size());
            done = true;
        } catch (RuntimeException error) {
            failure = error.getMessage();
        }
    }

    /** Fills the creative supplies, fits the electrode plate, wires the alternator to the panel and presses START, as a player would. */
    private static void start(ServerLevel level) {
        supply(level, SO2_SUPPLY, new FluidStack(CEUranium.SULFUR_DIOXIDE.get(), 1_000));
        supply(level, WATER_SUPPLY, new FluidStack(Fluids.WATER, 1_000));
        var cell = (ElectrolysisControllerBlockEntity) level.getBlockEntity(CELL.north());
        var rack = level.getCapability(Capabilities.ItemHandler.BLOCK, CELL.above(), Direction.UP);
        require(rack != null && rack.insertItem(1, CEElectrolysis.NICKEL_ELECTRODE_PLATE.toStack(), false).isEmpty(), "The nickel plate fits the head");
        require(cell.validate(), "The electrolysis cell forms: " + cell.status());
        var copper = CEERegistries.WIRE_TYPE.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
        var wiring = InfrastructureSavedData.load(level);
        for (int terminal = 0; terminal < 2; terminal++) wiring.connect(new InWorldNode(terminal, BRUSH), new InWorldNode(terminal, PANEL), copper);
        var player = FakePlayerFactory.getMinecraft(level);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.getBlockState(PANEL).useWithoutItem(level, player, new BlockHitResult(Vec3.atLowerCornerOf(PANEL).add(.75, .21, 0), Direction.NORTH, PANEL, false));
        require(cell.enabled(), "START enables the cell");
        var tower = (AbsorptionTowerBlockEntity) level.getBlockEntity(TOWER);
        require(tower.formed(), "The absorption tower forms: " + tower.status());
    }

    private static int amount(ServerLevel level, BlockPos pos, Direction face, Fluid fluid) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        int total = 0;
        if (handler != null) for (int tank = 0; tank < handler.getTanks(); tank++) if (handler.getFluidInTank(tank).is(fluid)) total += handler.getFluidInTank(tank).getAmount();
        return total;
    }

    private static void supply(ServerLevel level, BlockPos pos, FluidStack stack) {
        var tank = ((FluidTankBlockEntity) level.getBlockEntity(pos)).getControllerBE();
        ((CreativeFluidTankBlockEntity.CreativeSmartFluidTank) tank.getTankInventory()).setContainedFluid(stack);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_ALL); }
    private static void tank(ServerLevel level, BlockPos pos) { put(level, pos, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState()); }

    /** A stainless pipe open on the given sides. */
    private static void line(ServerLevel level, BlockPos pos, Direction... sides) {
        BlockState state = CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (Direction side : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), List.of(sides).contains(side));
        put(level, pos, state);
        LINES.add(pos);
    }

    private static void motor(ServerLevel level, BlockPos pos, Direction facing) {
        put(level, pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(RPM);
    }

    /** A stainless pump moving fluid toward {@code facing}, turned through a cog on {@code cogSide} by a motor beside that cog. */
    private static void pump(ServerLevel level, BlockPos pos, Direction facing, Direction cogSide, Direction motorSide) {
        put(level, pos, BuiltInRegistries.BLOCK.get(CreatePlant.location("stainless_steel_pump")).defaultBlockState().setValue(BlockStateProperties.FACING, facing));
        BlockPos cog = pos.relative(cogSide), motor = cog.relative(motorSide);
        put(level, cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, facing.getAxis()));
        motor(level, motor, motorSide.getOpposite());
        PUMPS.add(new BlockPos[] {pos, motor});
        LINES.add(pos);
    }

    private static void sign(ServerLevel level, BlockPos pos, String label) {
        put(level, pos, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) sign.setText(sign.getFrontText().setMessage(0, Component.literal(label)), true);
    }
}
