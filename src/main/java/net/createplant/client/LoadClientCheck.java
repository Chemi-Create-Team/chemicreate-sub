package net.createplant.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.TankFootprint;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEElectrolysis;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEGasPipes;
import net.createplant.thermal.FluidThermal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.LoggerFactory;

/**
 * Opt-in load measurement in the integrated world: each scenario builds many machines of one kind, lets them settle, and reads the server's
 * average tick time over the following ticks against the empty world. The results go to the log and to load-check.txt in the game directory.
 * createplant.loadScenarios picks scenarios by index (0 is the empty world), for example "0,4".
 */
final class LoadClientCheck {
    private static final int SETTLE = 200, MEASURE = 300, CLEAR = 100, SIZE = 128;
    private record Scenario(String name, Consumer<ServerLevel> build) { }
    private static final List<Scenario> SCENARIOS = List.of(
        new Scenario("baseline (empty)", level -> { }),
        new Scenario("64 open tanks, diameter 8, with water, on a beach", LoadClientCheck::openTanks),
        new Scenario("16 pumped lines of 30 Create pipes, 90 C water", LoadClientCheck::pumpedLines),
        new Scenario("16 gas pipe lines of 120 pipes between two tanks", LoadClientCheck::gasLines),
        new Scenario("128 filter-press electrolyzer rows (cells, plates, water), unpowered", LoadClientCheck::electrolyzers),
        new Scenario("256 Create tanks with 90 C water, 200 pigs against them", LoadClientCheck::hotTanks));
    private static final List<Scenario> RUN = selected();
    private static BlockPos origin;
    private static int scenario, ticks;
    private static final List<String> results = new ArrayList<>();
    /** Fills that wait until the placed machines have formed. */
    private static final List<Runnable> pending = new ArrayList<>();
    private static double baseline;

    private LoadClientCheck() { }

    private static List<Scenario> selected() {
        String chosen = System.getProperty("createplant.loadScenarios");
        if (chosen == null) return SCENARIOS;
        return java.util.Arrays.stream(chosen.split(",")).map(index -> SCENARIOS.get(Integer.parseInt(index.trim()))).toList();
    }

    static void start(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.setScreen(null);
        origin = mc.player.blockPosition().offset(8, 0, 8);
        scenario = 0;
        ticks = 0;
        build(mc);
    }

    /** Returns true once every scenario is measured. */
    static boolean tick(Minecraft mc) {
        if (mc.screen != null) mc.setScreen(null);
        ticks++;
        if (ticks == 10) {
            List<Runnable> fills = List.copyOf(pending);
            pending.clear();
            mc.getSingleplayerServer().execute(() -> fills.forEach(Runnable::run));
        }
        if (ticks == SETTLE + MEASURE) {
            var server = mc.getSingleplayerServer();
            // The server keeps the last 100 tick times; three windows of 100 cover the measurement.
            double mspt = server.getAverageTickTimeNanos() / 1e6;
            results.set(results.size() - 1, results.getLast() + String.format(Locale.ROOT, " %.3f", mspt));
            report();
            mc.getSingleplayerServer().execute(() -> clear(server.overworld()));
        } else if (ticks > SETTLE && ticks < SETTLE + MEASURE && (ticks - SETTLE) % 100 == 0) {
            double mspt = mc.getSingleplayerServer().getAverageTickTimeNanos() / 1e6;
            if (ticks - SETTLE == 100) results.add(RUN.get(scenario).name() + ":");
            results.set(results.size() - 1, results.getLast() + String.format(Locale.ROOT, " %.3f", mspt));
        }
        if (ticks >= SETTLE + MEASURE + CLEAR) {
            if (++scenario == RUN.size()) return true;
            ticks = 0;
            build(mc);
        }
        return false;
    }

    private static void build(Minecraft mc) {
        var scene = RUN.get(scenario);
        mc.getSingleplayerServer().execute(() -> scene.build().accept(mc.getSingleplayerServer().overworld()));
    }

    private static void report() {
        String line = results.getLast();
        String[] parts = line.substring(line.lastIndexOf(':') + 1).trim().split(" ");
        double sum = 0;
        for (String part : parts) sum += Double.parseDouble(part);
        double mean = sum / parts.length;
        if (scenario == 0) baseline = mean;
        line += String.format(Locale.ROOT, " | mean %.3f ms, +%.3f ms over the empty world", mean, mean - baseline);
        results.set(results.size() - 1, line);
        LoggerFactory.getLogger(LoadClientCheck.class).info("LOAD: {}", line);
        try {
            java.nio.file.Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve("load-check.txt"), results);
        } catch (java.io.IOException ignored) { }
    }

    private static void clear(ServerLevel level) {
        for (var mob : level.getEntities(EntityType.PIG, mob -> true)) mob.discard();
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) for (int y = 0; y < 6; y++) {
            BlockPos pos = origin.offset(x, y, z);
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        for (var item : level.getEntities(EntityType.ITEM, item -> true)) item.discard();
    }

    private static void openTanks(ServerLevel level) {
        BlockPos high = origin.offset(SIZE - 1, 6, SIZE - 1);
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(),
            "fillbiome " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " " + high.getX() + " " + high.getY() + " " + high.getZ() + " minecraft:beach");
        List<BlockPos> walls = new ArrayList<>();
        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) {
            BlockPos corner = origin.offset(i * 12, 0, j * 12);
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                if (!TankFootprint.contains(x, z, 8)) continue;
                level.setBlock(corner.offset(x, 0, z), CEBlocks.TANK_CASING.get().defaultBlockState(), 3);
                if (TankFootprint.isWall(x, z, 8)) for (int y = 1; y <= 3; y++) level.setBlock(corner.offset(x, y, z), CEBlocks.TANK_CASING.get().defaultBlockState(), 3);
            }
            walls.add(corner.offset(0, 1, 4));
        }
        later(() -> walls.forEach(wall -> fill(level, wall, Direction.WEST, new FluidStack(Fluids.WATER, 64000))));
    }

    private static void pumpedLines(ServerLevel level) {
        List<BlockPos> sources = new ArrayList<>();
        for (int line = 0; line < 16; line++) {
            BlockPos start = origin.offset(0, 0, line * 4);
            level.setBlock(start, AllBlocks.FLUID_TANK.getDefaultState(), 3);
            level.setBlock(start.east(33), AllBlocks.FLUID_TANK.getDefaultState(), 3);
            level.setBlock(start.east(1), pipe(AllBlocks.FLUID_PIPE.getDefaultState(), Direction.Axis.X), 3);
            level.setBlock(start.east(2), AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
            level.setBlock(start.east(2).above(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
            level.setBlock(start.east(1).above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
            for (int x = 3; x < 33; x++) level.setBlock(start.east(x), pipe(AllBlocks.FLUID_PIPE.getDefaultState(), Direction.Axis.X), 3);
            sources.add(start);
        }
        later(() -> sources.forEach(source -> fill(level, source, Direction.UP, FluidThermal.at(new FluidStack(Fluids.WATER, 8000), 363.15))));
    }

    private static void gasLines(ServerLevel level) {
        for (int line = 0; line < 16; line++) {
            BlockPos start = origin.offset(0, 0, line * 4);
            level.setBlock(start, AllBlocks.FLUID_TANK.getDefaultState(), 3);
            level.setBlock(start.east(121), AllBlocks.FLUID_TANK.getDefaultState(), 3);
            for (int x = 1; x <= 120; x++) level.setBlock(start.east(x), pipe(CEGasPipes.GAS_PIPE.get().defaultBlockState(), Direction.Axis.X), 3);
            later(() -> fill(level, start, Direction.UP, FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(), 40000), 293.15)));
        }
    }

    private static void electrolyzers(ServerLevel level) {
        for (int i = 0; i < 16; i++) for (int j = 0; j < 8; j++) {
            BlockPos low = origin.offset(i * 4, 0, j * 4);
            level.setBlock(low, CEElectrolysis.CELL.get().defaultBlockState(), 3);
            level.setBlock(low.above(), CEElectrolysis.HEAD.get().defaultBlockState(), 3);
            for (int y = 0; y < 2; y++) level.setBlock(low.north().above(y), CEElectrolysis.CONTROLLER.get().defaultBlockState(), 3);
            later(() -> {
                var owner = (net.createplant.block.entity.ElectrolysisControllerBlockEntity)level.getBlockEntity(low.north());
                owner.validate();
                if (level.getBlockEntity(low.above()) instanceof net.createplant.block.entity.ElectrodeHeadBlockEntity head) head.electrodes.insertItem(0, CEElectrolysis.ELECTRODE_PLATE.toStack(8), false);
                fill(level, low, Direction.DOWN, new FluidStack(Fluids.WATER, 1000));
            });
        }
    }

    private static void hotTanks(ServerLevel level) {
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) {
            BlockPos pos = origin.offset(i * 3, 0, j * 3);
            level.setBlock(pos, AllBlocks.FLUID_TANK.getDefaultState(), 3);
            later(() -> fill(level, pos, Direction.UP, FluidThermal.at(new FluidStack(Fluids.WATER, 8000), 363.15)));
            if (i * 16 + j < 200 && EntityType.PIG.create(level) instanceof Mob pig) {
                pig.setNoAi(true);
                pig.setInvulnerable(true);
                pig.moveTo(pos.getX() + 1.46, pos.getY(), pos.getZ() + .5);
                level.addFreshEntity(pig);
            }
        }
    }

    private static BlockState pipe(BlockState state, Direction.Axis axis) {
        for (Direction d : Direction.values()) state = state.setValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(d), d.getAxis() == axis);
        return state;
    }

    private static void fill(ServerLevel level, BlockPos pos, Direction side, FluidStack stack) {
        var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (handler != null) handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        if (level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank) tank.sendData();
    }

    private static void later(Runnable task) {
        pending.add(task);
    }
}
