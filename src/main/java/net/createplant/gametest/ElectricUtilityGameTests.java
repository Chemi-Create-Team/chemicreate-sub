package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import net.createplant.CreatePlant;
import net.createplant.block.ElectricPumpBlock;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

import java.nio.file.*;
import java.util.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ElectricUtilityGameTests {
    private static final Map<String, Object> RESULTS = new LinkedHashMap<>();

    private static void result(String key, Object value) {
        RESULTS.put(key, value);
        try {
            Files.writeString(
                    Path.of(
                            System.getProperty(
                                    "createplant.electricUtilityReport",
                                    "electric-utilities.json")),
                    new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BlockPos battery(GameTestHelper h, BlockPos pos) {
        h.getLevel()
                .setBlock(
                        pos,
                        BuiltInRegistries.BLOCK
                                .get(
                                        ResourceLocation.fromNamespaceAndPath(
                                                "electroenergetics", "creative_battery"))
                                .defaultBlockState(),
                        3);
        return pos;
    }

    private static CreativeBatteryDevice wire(
            GameTestHelper h, BlockPos battery, BlockPos device, double volts) {
        var level = h.getLevel();
        var source = DevicesSavedData.load(level).getDevice(battery, CreativeBatteryDevice.class);
        h.assertTrue(source != null, "Creative battery has real circuit nodes");
        source.voltage = volts;
        source.acFrequency = 0;
        var copper =
                CEERegistries.WIRE_TYPE.get(
                        ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper"));
        for (int node = 0; node < 2; node++)
            InfrastructureSavedData.load(level)
                    .connect(new InWorldNode(node, battery), new InWorldNode(node, device), copper);
        return source;
    }

    private record Line(ElectricPumpBlockEntity pump, FluidTankBlockEntity from, FluidTankBlockEntity to, BlockPos stop) {}

    @GameTest(template = "test_fusion", timeoutTicks = 420)
    public static void nativePipesWorkInAllSixDirectionsAndRestartAfterStopping(GameTestHelper h) {
        var level = h.getLevel();
        var lines = new ArrayList<Line>();
        int index = 0;
        for (var direction : Direction.values()) {
            var pos = h.absolutePos(new BlockPos(5 + index % 3 * 16, 5, 5 + index / 3 * 18));
            index++;
            level.setBlock(
                    pos,
                    CEElectricUtilities.PUMP
                            .get()
                            .defaultBlockState()
                            .setValue(ElectricPumpBlock.FACING, direction),
                    3);
            for (int sign : new int[] {-1, 1}) {
                var side = sign < 0 ? direction.getOpposite() : direction;
                var tank = pos.relative(side, 2);
                level.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState(), 3);
                var pipe = AllBlocks.FLUID_PIPE.getDefaultState();
                for (var face : Direction.values())
                    pipe =
                            pipe.setValue(
                                    FluidPipeBlock.PROPERTY_BY_DIRECTION.get(face),
                                    face.getAxis() == direction.getAxis());
                level.setBlock(pos.relative(side), pipe, 3);
            }
            var sourceTank = pos.relative(direction.getOpposite(), 2);
            if (direction == Direction.UP) {
                level.removeBlock(sourceTank, false);
                sourceTank = pos.below().west(2);
                level.setBlock(sourceTank, AllBlocks.FLUID_TANK.getDefaultState(), 3);
                var bridge = AllBlocks.FLUID_PIPE.getDefaultState();
                for (var face : Direction.values())
                    bridge =
                            bridge.setValue(
                                    FluidPipeBlock.PROPERTY_BY_DIRECTION.get(face),
                                    face.getAxis() == Direction.Axis.X);
                level.setBlock(pos.below().west(), bridge, 3);
                var elbow = AllBlocks.FLUID_PIPE.getDefaultState();
                for (var face : Direction.values())
                    elbow =
                            elbow.setValue(
                                    FluidPipeBlock.PROPERTY_BY_DIRECTION.get(face),
                                    face == Direction.WEST || face == Direction.UP);
                level.setBlock(pos.below(), elbow, 3);
            }
            var from = (FluidTankBlockEntity) level.getBlockEntity(sourceTank);
            var to = (FluidTankBlockEntity) level.getBlockEntity(pos.relative(direction, 2));
            var pump = (ElectricPumpBlockEntity) level.getBlockEntity(pos);
            var stop =
                    pos.relative(
                            direction.getAxis() == Direction.Axis.Y
                                    ? Direction.EAST
                                    : Direction.UP);
            var line = new Line(pump, from, to, stop);
            lines.add(line);
            var source = battery(h, pos.offset(5, 0, 5));
            h.runAfterDelay(
                    25,
                    () -> {
                        ConnectivityHandler.formMulti(from);
                        ConnectivityHandler.formMulti(to);
                        from.getTankInventory()
                                .fill(
                                        FluidThermal.at(new FluidStack(Fluids.WATER, 4000), 333.15),
                                        EXECUTE);
                        wire(h, source, pos, 400);
                    });
        }
        h.runAfterDelay(
                165,
                () -> {
                    var rows = new ArrayList<Map<String, Object>>();
                    for (var line : lines)
                        org.slf4j.LoggerFactory.getLogger(ElectricUtilityGameTests.class)
                                .info(
                                        "PUMP DIAG {}: {} -> {}, rpm={}, W={}, J={}, work={},"
                                                + "",
                                        line.pump
                                                .getBlockState()
                                                .getValue(ElectricPumpBlock.FACING),
                                        line.from.getTankInventory().getFluidAmount(),
                                        line.to.getTankInventory().getFluidAmount(),
                                        line.pump.getSpeed(),
                                        line.pump.watts(),
                                        line.pump.storedJoules(),
                                        line.pump.workJoules());
                    for (var line : lines) {
                        var pump = line.pump;
                        int a = line.from.getTankInventory().getFluidAmount(),
                                b = line.to.getTankInventory().getFluidAmount();
                        h.assertTrue(
                                b > 0 && a + b == 4000,
                                "Real Create pipes transfer exactly 4000 mB in "
                                        + pump.getBlockState().getValue(ElectricPumpBlock.FACING)
                                        + ": "
                                        + a
                                        + " / "
                                        + b
                                        + " rpm "
                                        + pump.getSpeed());
                        h.assertTrue(
                                pump.watts() > 4500
                                        && pump.getSpeed() > 100
                                        && pump.workJoules() > 0,
                                "The real circuit pays for the pump's pressure work: "
                                        + pump.watts()
                                        + " / "
                                        + pump.workJoules());
                        for (var face : Direction.values())
                            h.assertTrue(
                                    !CEElectricUtilities.PUMP
                                            .get()
                                            .hasShaftTowards(
                                                    level,
                                                    pump.getBlockPos(),
                                                    pump.getBlockState(),
                                                    face),
                                    "No external shaft drive");
                        h.assertTrue(
                                !CEElectricUtilities.PUMP.get().isSmallCog(), "No cogwheel drive");
                        rows.add(
                                Map.of(
                                        "direction",
                                        pump.getBlockState()
                                                .getValue(ElectricPumpBlock.FACING)
                                                .getName(),
                                        "input_mB",
                                        a,
                                        "output_mB",
                                        b,
                                        "watts",
                                        pump.watts(),
                                        "rpm",
                                        pump.getSpeed(),
                                        "work_J",
                                        pump.workJoules()));
                        level.setBlock(line.stop, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    }
                    result("pump_six_directions", rows);
                });
        h.runAfterDelay(
                190,
                () -> {
                    for (var line : lines) {
                        h.assertTrue(
                                line.pump.getSpeed() == 0 && line.pump.watts() == 0,
                                "Redstone stops both rotation and electrical load");
                        var saved = line.pump.saveWithoutMetadata(level.registryAccess());
                        var copy =
                                new ElectricPumpBlockEntity(
                                        line.pump.getBlockPos(), line.pump.getBlockState());
                        copy.loadWithComponents(saved, level.registryAccess());
                        h.assertTrue(
                                copy.storedJoules() == line.pump.storedJoules()
                                        && copy.getSpeed() == 0,
                                "Saving motor reserves never adds energy or free rotation");
                        line.from
                                .getTankInventory()
                                .fill(new FluidStack(Fluids.WATER, 4000), EXECUTE);
                        level.removeBlock(line.stop, false);
                    }
                });
        h.runAfterDelay(
                300,
                () -> {
                    for (var line : lines)
                        h.assertTrue(
                                line.to.getTankInventory().getFluidAmount() > 4000
                                        && line.from.getTankInventory().getFluidAmount()
                                                        + line.to
                                                                .getTankInventory()
                                                                .getFluidAmount()
                                                == 8000,
                                "All six motors restart and retain both finite charges");
                    var line = lines.get(2);
                    var player =
                            net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(
                                    level);
                    player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack());
                    var pos = line.pump.getBlockPos();
                    var old = level.getBlockState(pos).getValue(ElectricPumpBlock.FACING);
                    CEElectricUtilities.PUMP
                            .get()
                            .onWrenched(
                                    level.getBlockState(pos),
                                    new UseOnContext(
                                            player,
                                            InteractionHand.MAIN_HAND,
                                            new BlockHitResult(
                                                    Vec3.atCenterOf(pos),
                                                    Direction.UP,
                                                    pos,
                                                    false)));
                    h.assertTrue(
                            level.getBlockState(pos).getValue(ElectricPumpBlock.FACING)
                                    == old.getOpposite(),
                            "Native wrench reverses the pumping face");
                });
        h.runAfterDelay(
                390,
                () -> {
                    var line = lines.get(2);
                    h.assertTrue(
                            line.from.getTankInventory().getFluidAmount() > 0,
                            "Wrench reversal actually returns fluid through the same native pipes");
                    level.removeBlock(line.pump.getBlockPos().offset(5, 0, 5), false);
                });
        h.runAfterDelay(
                405,
                () -> {
                    var pump = lines.get(2).pump;
                    h.assertTrue(
                            pump.watts() == 0 && pump.getSpeed() == 0,
                            "A real power loss stops the native pump");
                    h.succeed();
                });
    }

}
