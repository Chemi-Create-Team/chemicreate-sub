package net.createplant.client;

import net.createplant.thermal.FluidForm;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.createplant.block.FusionConsolePanelBlock;
import net.createplant.block.FusionControlConsoleBlock;
import net.createplant.block.FusionMotorGeneratorBlock;
import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.createplant.block.entity.FusionMotorGeneratorBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEDisplays;
import net.createplant.registry.CEFusion;
import net.createplant.registry.CETransport;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in creative flat world with a formed tokamak ready for a player to commission from the desk. Creative tanks supply
 * D2, T2, 4.2 K helium and water and absorb the outlets; a bank of creative motors stands in for the turbine start power.
 * {@code -Dcreateplant.fusionPlaytest=world} saves it for play, {@code verify} commissions it to a burn and checks the cooling.
 */
public final class FusionPlaytestWorld {
    private static final Logger LOG = LoggerFactory.getLogger(FusionPlaytestWorld.class);
    private static final boolean VERIFY = "verify".equals(System.getProperty("createplant.fusionPlaytest"));
    private static final String WORLD = "createplant-fusion-" + (VERIFY ? "verify-" : "playtest-") + System.currentTimeMillis();
    /** Charging draws 120 MW through the M-G; each creative motor carries 16,384 SU per RPM, about 4.2 MW at 256 RPM. */
    private static final int START_MOTORS = 36, RPM = 256, BURN_HOLD_TICKS = 6_000;
    private static final BlockPos CONTROLLER = new BlockPos(0, -60 + FusionPhysics.MIDPLANE, 0);
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<BlockPos[]> PUMPS = new ArrayList<>();
    private static volatile String failure;
    private static volatile boolean done;
    private static int phase, ticks;
    private static long serverTicks, stageStart;
    private static double lastBlanketKelvin;
    private static int stage;

    private FusionPlaytestWorld() { }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0 && mc.screen != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 1;
                ticks = 0;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings("Create-Plant fusion reactor test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            } else if (phase == 1 && mc.level != null && mc.getOverlay() == null && ++ticks > 80) {
                phase = 2;
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        build(server.overworld(), server.getPlayerList().getPlayers().getFirst());
                        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, FusionPlaytestWorld::serverTick);
                    } catch (RuntimeException error) {
                        failure = error.toString();
                    }
                });
            } else if (phase == 2) {
                if (failure != null) throw new IllegalStateException(failure);
                if (!done) return;
                phase = 3;
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.saveEverything(false, true, true));
            } else if (phase == 3 && ++ticks > 40) {
                phase = -2;
                Files.writeString(mc.gameDirectory.toPath().resolve("fusion-playtest-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
                LOG.info("FUSION PLAYTEST {}: {}", VERIFY ? "VERIFIED" : "WORLD READY", REPORT);
                mc.stop();
            }
        } catch (Exception error) {
            phase = -1;
            LOG.error("FUSION PLAYTEST FAILED: {}", REPORT, error);
            mc.stop();
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        var server = level.getServer();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (FusionStructure.Cell cell : FusionStructure.cells(CONTROLLER, Direction.SOUTH))
            if (cell.role() != FusionStructure.Role.CLEAR) put(level, cell.pos(), cell.expected());
        BlockPos centre = FusionStructure.centre(CONTROLLER, Direction.SOUTH);
        int half = FusionPhysics.HALF_WIDTH;

        // North: the D2 and T2 ports are stacked, so each leaves through an encased elbow that cannot join the other line.
        BlockPos front = CONTROLLER.north();
        put(level, front.below(2), encased(Direction.SOUTH, Direction.WEST));
        put(level, front.below(2).west(), pipe(Direction.EAST, Direction.WEST));
        pump(level, front.below(2).west(2), Direction.EAST, Direction.DOWN, Direction.WEST);
        gasTank(level, front.below(2).west(3), Direction.Axis.X);
        put(level, front.below(), encased(Direction.SOUTH, Direction.EAST));
        put(level, front.below().east(), pipe(Direction.WEST, Direction.EAST));
        pump(level, front.below().east(2), Direction.WEST, Direction.UP, Direction.EAST);
        gasTank(level, front.below().east(3), Direction.Axis.X);
        put(level, front.above(), pipe(Direction.SOUTH, Direction.EAST));
        pump(level, front.above().east(), Direction.EAST, Direction.UP, Direction.EAST);
        gasTank(level, front.above().east(2), Direction.Axis.X);

        // South: liquid helium in below the midplane, boil-off out above it.
        BlockPos south = centre.south(half + 1);
        pump(level, south.below(), Direction.NORTH, Direction.EAST, Direction.SOUTH);
        tank(level, south.below().south());
        pump(level, south.above(), Direction.SOUTH, Direction.EAST, Direction.SOUTH);
        gasTank(level, south.above().south(), Direction.Axis.Z);

        // East: once-through blanket water from one creative tank to another. The ultra-high-pressure segment beside each pump
        // makes it a main; an ordinary pump connection carries far less than the burning blanket heats.
        BlockPos east = centre.east(half + 1);
        pump(level, east.below(), Direction.WEST, Direction.DOWN, Direction.EAST);
        put(level, east.below().east(), pipe(Direction.WEST, Direction.EAST));
        // A one-block creative tank hands over only its 8,000 mB a tick; the burn needs about 23,000 mB/t of cold water.
        for (int x = 2; x <= 4; x++) for (int z = -1; z <= 1; z++) tank(level, east.below().east(x).south(z));
        pump(level, east.above(), Direction.EAST, Direction.UP, Direction.EAST);
        put(level, east.above().east(), pipe(Direction.WEST, Direction.EAST));
        tank(level, east.above().east(2));

        BlockPos vacuumMotor = centre.west(half + 1);
        motor(level, vacuumMotor, Direction.EAST);

        // The M-G and flywheel sit on the controller's shaft face; the motor bank drives them through a chain-drive row.
        put(level, front, CEFusion.MOTOR_GENERATOR.get().defaultBlockState().setValue(FusionMotorGeneratorBlock.AXIS, Direction.Axis.Z));
        put(level, front.north(), CEBlocks.LARGE_FLYWHEEL.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        put(level, front.north(2), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        for (int i = 0; i < START_MOTORS; i++) {
            BlockPos drive = front.north(3).west(START_MOTORS / 2 - 1).east(i);
            put(level, drive, AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
            motor(level, drive.north(), Direction.SOUTH);
        }

        BlockPos master = CONTROLLER.offset(7, -FusionPhysics.MIDPLANE, -2);
        for (int depth = 0; depth < FusionControlConsoleBlockEntity.DEPTH; depth++)
            for (int column = 0; column < FusionControlConsoleBlockEntity.WIDTH; column++) {
                BlockPos at = master.west(column - FusionControlConsoleBlockEntity.MASTER_COLUMN).north(depth);
                put(level, at, column == FusionControlConsoleBlockEntity.MASTER_COLUMN && depth == FusionControlConsoleBlockEntity.MASTER_DEPTH
                    ? CEFusion.CONSOLE.get().defaultBlockState().setValue(FusionControlConsoleBlock.FACING, Direction.NORTH)
                    : CEFusion.CONSOLE_PANEL.get().defaultBlockState().setValue(FusionConsolePanelBlock.FACING, Direction.NORTH));
            }

        BlockPos chest = master.offset(3, 0, 2);
        put(level, chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof Container spares) {
            spares.setItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get(), 2));
            spares.setItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get(), 2));
            spares.setItem(2, AllItems.WRENCH.asStack());
        }
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        BlockPos spawn = master.offset(2, 0, 3);
        player.teleportTo(level, spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 70, 10);
        player.setRespawnPosition(Level.OVERWORLD, spawn, 70, true, false);
        level.setDefaultSpawnPos(spawn, 70);
        REPORT.put("world", WORLD);
        REPORT.put("controller", CONTROLLER.toShortString());
        REPORT.put("console", master.toShortString());
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
            if (serverTicks == 20) supply(level);
            if (serverTicks < 200) return;
            var controller = (FusionReactorControllerBlockEntity) level.getBlockEntity(CONTROLLER);
            var console = (FusionControlConsoleBlockEntity) level.getBlockEntity(CONTROLLER.offset(7, -FusionPhysics.MIDPLANE, -2));
            require(controller != null && console != null, "The generated world was left before the checks finished");
            if (serverTicks == 200) {
                require(controller.formed(), "The reactor formed: " + controller.status());
                require(console.linked(), "The desk is linked through the Display Link");
                require(!controller.deuterium().isEmpty() && !controller.tritium().isEmpty(), "D2 and T2 reach the controller");
                require(!controller.liquidHelium().isEmpty(), "Liquid helium reaches the controller");
                require(!controller.coolantIn().isEmpty(), "Blanket water reaches the controller");
                REPORT.put("readyStatus", controller.status());
                if (!VERIFY) { done = true; return; }
                stageStart = serverTicks;
            }
            commission(level, controller, console);
        } catch (RuntimeException error) {
            failure = error.getMessage();
        }
    }

    /** Uses only the desk switches, in the operating order a player follows. */
    private static void commission(ServerLevel level, FusionReactorControllerBlockEntity controller, FusionControlConsoleBlockEntity console) {
        long elapsed = serverTicks - stageStart;
        require(stage == 5 || elapsed < 12_000, "Commissioning stuck at stage " + stage + ": " + controller.status());
        var generator = (FusionMotorGeneratorBlockEntity) level.getBlockEntity(CONTROLLER.north());
        require(!"overload".equals(generator.status()), "The start motor bank carries the M-G load");
        switch (stage) {
            case 0 -> next(level, console, 0, 0);
            case 1 -> { if (FusionPhysics.vacuumReady(controller.baseVacuumPascals())) next(level, console, 2, 0); }
            case 2 -> { if (controller.coilKelvin() < 5) next(level, console, 0, 1); }
            case 3 -> { if (controller.magnetCharge() >= 1) next(level, console, 1, 1); }
            case 4 -> {
                if (controller.fusionWatts() <= 0) return;
                REPORT.put("burningAtTick", serverTicks);
                stage = 5;
                stageStart = serverTicks;
            }
            case 5 -> {
                if (elapsed == BURN_HOLD_TICKS - 500) lastBlanketKelvin = controller.blanketKelvin();
                if (elapsed % 250 == 0 || controller.fusionWatts() <= 0)
                    REPORT.put("burn" + elapsed, String.format(java.util.Locale.ROOT, "%s blanket %.1f C, flow %.0f, in %d, out %d",
                        controller.status(), controller.blanketKelvin() - 273.15, controller.coolantFlow(),
                        controller.coolantIn().getAmount(), controller.coolantOut().getAmount()));
                require(controller.fusionWatts() > 0 && !controller.emergency(), "The burn holds: " + controller.status());
                if (elapsed < BURN_HOLD_TICKS) return;
                require(controller.coolantFlow() > 0, "Blanket water flows through the burning reactor");
                require(controller.coolantIn().getAmount() > 8_000, "The water supply keeps ahead of the 8,000 mB a single-block tank could give");
                require(controller.coolantOut().getAmount() < FusionReactorControllerBlockEntity.COOLANT_CAPACITY,
                    "The outlet pump keeps the heated water draining: " + controller.coolantOut().getAmount() + " mB held");
                require(controller.blanketKelvin() - lastBlanketKelvin < 2, "The blanket settles at a steady temperature");
                require(controller.blanketKelvin() < FusionPhysics.COOLANT_OUTLET_KELVIN + 10, "The blanket stays near its design ceiling");
                REPORT.put("fusionMW", controller.fusionWatts() / 1e6);
                REPORT.put("electricMW", generator.electricalWatts() / 1e6);
                REPORT.put("blanketCelsius", controller.blanketKelvin() - 273.15);
                REPORT.put("coolantFlow", controller.coolantFlow());
                REPORT.put("coolantOutMb", controller.coolantOut().getAmount());
                REPORT.put("heldTicks", elapsed);
                done = true;
            }
            default -> { }
        }
    }

    private static void next(ServerLevel level, FusionControlConsoleBlockEntity console, int column, int depth) {
        BlockPos at = console.panel(column, depth);
        var player = level.getServer().getPlayerList().getPlayers().getFirst();
        level.getBlockState(at).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        REPORT.put("stage" + stage + "AtTick", serverTicks);
        stage++;
        stageStart = serverTicks;
    }

    private static void supply(ServerLevel level) {
        BlockPos front = CONTROLLER.north();
        BlockPos centre = FusionStructure.centre(CONTROLLER, Direction.SOUTH);
        int half = FusionPhysics.HALF_WIDTH;
        fill(level, front.below(2).west(3), new FluidStack(CEFusion.DEUTERIUM_GAS.get(), 1_000));
        fill(level, front.below().east(3), new FluidStack(CEFusion.TRITIUM_GAS.get(), 1_000));
        fill(level, centre.south(half + 2).below(),
            FluidThermal.at(FluidForm.LIQUID_HELIUM.stack(1_000), FusionPhysics.LIQUID_HELIUM_KELVIN));
        fill(level, centre.east(half + 3).below(), new FluidStack(Fluids.WATER, 1_000));
        var blanket = ((FusionReactorControllerBlockEntity) level.getBlockEntity(CONTROLLER)).blanketPort();
        require(blanket.insertItem(0, new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false).isEmpty(), "The blanket cartridge installs");
        require(blanket.insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false).isEmpty(), "The divertor cassette installs");

        BlockPos linkPos = front.east();
        put(level, linkPos, AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, Direction.EAST));
        var link = (DisplayLinkBlockEntity) level.getBlockEntity(linkPos);
        link.activeSource = CEDisplays.MACHINE.get();
        link.activeTarget = CEDisplays.FUSION_CONSOLE.get();
        link.getSourceConfig().putInt("Metric", MachineReadings.KEYS.indexOf("fusion_power"));
        link.target(CONTROLLER.offset(7, -FusionPhysics.MIDPLANE, -2));
        link.updateGatheredData();
    }

    private static void fill(ServerLevel level, BlockPos pos, FluidStack stack) {
        var tank = ((com.simibubi.create.content.fluids.tank.FluidTankBlockEntity) level.getBlockEntity(pos)).getControllerBE();
        if (tank instanceof CreativeFluidTankBlockEntity) ((CreativeFluidTankBlockEntity.CreativeSmartFluidTank) tank.getTankInventory()).setContainedFluid(stack);
        else tank.getTankInventory().fill(stack.copyWithAmount(512000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_ALL); }

    private static void gasTank(ServerLevel level, BlockPos pos, Direction.Axis axis) { put(level, pos, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS, axis)); }
    private static void tank(ServerLevel level, BlockPos pos) { put(level, pos, AllBlocks.CREATIVE_FLUID_TANK.getDefaultState()); }

    private static BlockState pipe(Direction... sides) {
        BlockState state = CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (Direction side : Direction.values()) state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), List.of(sides).contains(side));
        return state;
    }

    private static BlockState encased(Direction... sides) {
        BlockState state = AllBlocks.ENCASED_FLUID_PIPE.getDefaultState();
        for (Direction side : Direction.values()) state = state.setValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(side), List.of(sides).contains(side));
        return state;
    }

    private static void motor(ServerLevel level, BlockPos pos, Direction facing) {
        put(level, pos, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(RPM);
    }

    /** A Create pump moving fluid toward {@code facing}, turned through a cog on {@code cogSide} by a motor beside that cog. */
    private static void pump(ServerLevel level, BlockPos pos, Direction facing, Direction cogSide, Direction motorSide) {
        put(level, pos, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, facing));
        BlockPos cog = pos.relative(cogSide), motor = cog.relative(motorSide);
        put(level, cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, facing.getAxis()));
        motor(level, motor, motorSide.getOpposite());
        PUMPS.add(new BlockPos[] {pos, motor});
    }
}
