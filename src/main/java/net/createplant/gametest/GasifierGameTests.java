package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.EnumSet;
import net.createplant.CreatePlant;
import net.createplant.block.GasifierBlock;
import net.createplant.block.PulsationTubeBlock;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.*;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.*;
import static net.createplant.block.GasifierBlock.Role.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GasifierGameTests {
    private static final BlockPos BASE = new BlockPos(5, 2, 5);
    private static final BlockPos NORTH_NOZZLE = BASE.above(3).north();

    private static GasifierBlockEntity build(GameTestHelper h, EnumSet<Direction> nozzles) {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var pos = BASE.offset(x, y, z);
            boolean center = x == 0 && z == 0;
            if (center && (y == 1 || y == 3 || y == 4)) {
                h.setBlock(pos, Blocks.AIR);
                continue;
            }
            var role = y == 2 && center ? HEARTH : y == 3 || y == 4 ? WALL : CASING;
            var face = Direction.NORTH;
            if ((y == 1 || y == 4) && x == 1 && z == 0) role = WINDOW;
            if (y == 3 && Math.abs(x) + Math.abs(z) == 1) {
                var direction = Direction.fromDelta(x, 0, z);
                if (nozzles.contains(direction)) {
                    role = NOZZLE;
                    face = direction;
                }
            }
            h.setBlock(pos, CEGasifier.PARTS.get(role).get().defaultBlockState().setValue(GasifierBlock.FACING, face));
        }
        return (GasifierBlockEntity) h.getBlockEntity(BASE);
    }

    private static IFluidHandler fluid(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static IItemHandler items(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static void charge(GameTestHelper h, int carbon, FluidStack steam) {
        h.assertTrue(items(h, NORTH_NOZZLE, Direction.NORTH).insertItem(0, CEItems.CARBON_POWDER.toStack(carbon), false).isEmpty(),
            "The nozzle accepts carbon powder from item automation");
        h.assertTrue(fluid(h, NORTH_NOZZLE, Direction.NORTH).fill(steam, EXECUTE) == steam.getAmount(),
            "The nozzle accepts the supplied steam");
    }

    private static void burner(GameTestHelper h, BlazeBurnerBlock.HeatLevel heat) {
        h.setBlock(BASE.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
    }

    private static void ticks(GasifierBlockEntity gasifier, int count) {
        for (int tick = 0; tick < count; tick++) gasifier.tick();
    }

    @GameTest(template = "test_petrochemical")
    public static void placementMultipliersAndSharedPorts(GameTestHelper h) {
        for (var directions : java.util.List.of(
            EnumSet.noneOf(Direction.class), EnumSet.of(Direction.NORTH), EnumSet.of(Direction.NORTH, Direction.EAST),
            EnumSet.of(Direction.NORTH, Direction.SOUTH), EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST),
            EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST))) {
            var be = build(h, directions);
            h.assertTrue(be.rebuild() == !directions.isEmpty(), "A real shell requires at least one correctly placed nozzle");
            double expected = switch (directions.size()) {
                case 0 -> 0; case 1 -> 1; case 2 -> directions.contains(Direction.SOUTH) ? 2.4 : 1.6;
                case 3 -> 3; default -> 4.5;
            };
            h.assertTrue(be.speedMultiplier() == expected, "Correct bonus for " + directions);
        }
        h.assertTrue(fluid(h, NORTH_NOZZLE, Direction.SOUTH) == null && fluid(h, BASE.east(), Direction.WEST) == null,
            "Only the outward connection faces have capabilities");
        var gas = fluid(h, BASE.above(5), Direction.UP);
        h.assertTrue(gas.getTankCapacity(0) == 16000 && gas.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 250), EXECUTE) == 0,
            "The top face is a 16000 mB output only");
        var input = fluid(h, NORTH_NOZZLE, Direction.NORTH);
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 250), EXECUTE) == 0, "The nozzle rejects liquid water");
        var pipePos = NORTH_NOZZLE.north();
        h.setBlock(pipePos, AllBlocks.FLUID_PIPE.getDefaultState());
        var state = h.getBlockState(pipePos);
        h.assertTrue(state.getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH)), "Create's placed pipe connects toward the nozzle");
        h.setBlock(BASE.above(4), Blocks.STONE);
        var be = (GasifierBlockEntity) h.getBlockEntity(BASE);
        h.assertTrue(!be.formed() && !be.rebuild() && input.getTankCapacity(0) == 0, "A blocked chamber immediately revokes old ports");
        h.setBlock(BASE.above(4), Blocks.AIR);
        h.assertTrue(be.rebuild() && input.getTankCapacity(0) == 0, "Reformation cannot revive an invalidated handler");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void externalHeatAndExactBatch(GameTestHelper h) {
        var be = build(h, EnumSet.of(Direction.NORTH, Direction.SOUTH));
        h.assertTrue(be.rebuild(), "Gasifier forms");
        var nozzle = fluid(h, NORTH_NOZZLE, Direction.NORTH);
        var steam = new FluidStack(CEFluids.STEAM.get(), 9000);
        h.assertTrue(nozzle.getTanks() == 1 && nozzle.fill(steam, SIMULATE) == 8000 && nozzle.getFluidInTank(0).isEmpty(),
            "The steam tank holds 8000 mB and simulation leaves it empty");
        h.assertTrue(nozzle.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 250), EXECUTE) == 0
                && nozzle.fill(new FluidStack(CEProcessFluids.COMPRESSED_AIR.get(), 250), EXECUTE) == 0,
            "The inlet rejects both former oxidizers");
        charge(h, 2, steam.copyWithAmount(500));
        h.assertTrue(nozzle.drain(250, SIMULATE).isEmpty() && fluid(h, BASE.west(), Direction.WEST) == null
            && fluid(h, BASE.east(), Direction.EAST) == null && fluid(h, BASE.south(), Direction.SOUTH) == null,
            "A fresh gasifier has an inlet and gas outlet only");
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        ticks(be, 75);
        h.assertTrue(be.status().equals("heat_required") && be.temperature() == 20 && be.itemCount(0) == 2,
            "A smouldering burner leaves the cold inventory untouched");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(be, 53);
        h.assertTrue(be.temperature() < 1300 && be.status().equals("preheat") && be.progress() == 0,
            "An active burner preheats the empty chamber without committing feed");
        ticks(be, 16);
        h.assertTrue(be.fluid(2).isEmpty(), "Sixteen hot ticks at 2.4x are insufficient");
        ticks(be, 1);
        var output = fluid(h, BASE.above(5), Direction.UP);
        var gas = output.drain(500, EXECUTE);
        h.assertTrue(gas.is(CEProcessFluids.WATER_GAS.get()) && gas.getAmount() == 500
            && gas.get(CEThermal.HEAT_GRADE.get()) == Grade.Heat.VERY_HOT.ordinal()
            && FluidHazards.of(gas).gas(), "Top output delivers 500 mB of very hot water gas");
        h.assertTrue(be.itemCount(0) == 1 && nozzle.getFluidInTank(0).getAmount() == 250
            && items(h, BASE, Direction.DOWN) == null, "Each batch consumes one carbon powder and 250 mB of steam with no slag");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void lossOfExternalHeatPausesBatch(GameTestHelper h) {
        var be = build(h, EnumSet.of(Direction.NORTH));
        h.assertTrue(be.rebuild(), "Gasifier forms");
        charge(h, 1, new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(), 250));
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(be, 65);
        double progress = be.progress();
        h.assertTrue(progress == 12 && be.temperature() == 1450, "The burner has formed a partial batch");
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        ticks(be, 30);
        h.assertTrue(be.status().equals("heat_required") && be.temperature() > 1300 && be.progress() == progress
            && be.itemCount(0) == 1 && be.fluid(1).getAmount() == 250 && be.fluid(2).isEmpty(),
            "A hot chamber still pauses immediately when its external heat stops");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(be, 28);
        h.assertTrue(be.fluid(2).getAmount() == 500 && be.itemCount(0) == 0 && be.fluid(1).isEmpty(),
            "Restoring heat completes the same batch exactly once");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void blockedOutputPreservesFeed(GameTestHelper h) {
        var be = build(h, EnumSet.of(Direction.NORTH, Direction.SOUTH));
        h.assertTrue(be.rebuild(), "Gasifier forms");
        charge(h, 33, new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 8000));
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(be, 600);
        fluid(h, NORTH_NOZZLE, Direction.NORTH).fill(new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 250), EXECUTE);
        ticks(be, 40);
        h.assertTrue(be.status().equals("gas_output") && be.fluid(2).getAmount() == 16000
            && be.itemCount(0) == 1 && be.fluid(1).getAmount() == 250, "A full tank pauses with feed intact");
        var output = fluid(h, BASE.above(5), Direction.UP);
        output.drain(500, EXECUTE);
        ticks(be, 17);
        h.assertTrue(be.fluid(2).getAmount() == 16000 && be.itemCount(0) == 0 && be.fluid(1).isEmpty(),
            "Freeing one batch of space restarts production");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 320)
    public static void pumpedOutputCanBeCooledAndStored(GameTestHelper h) {
        var gasifier = build(h, EnumSet.of(Direction.NORTH));
        h.assertTrue(gasifier.rebuild(), "Gasifier forms");
        charge(h, 1, new FluidStack(CEFluids.STEAM.get(), 250));
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        var furnace = BASE.below();
        h.assertTrue(BlazeBurnerBlock.tryInsert(h.getBlockState(furnace), h.getLevel(), h.absolutePos(furnace),
            Items.COAL.getDefaultInstance(), false, false, false).getResult().consumesAction(), "The external burner lights with ordinary coal");

        var elbow = BASE.above(6);
        var pump = elbow.east();
        var exchanger = ThermalControlGameTests.build(h.getLevel(), h.absolutePos(pump.east(2)), Direction.NORTH, 2);
        var receivingTube = ThermalControlGameTests.shell(exchanger, 0);
        h.assertTrue(receivingTube.fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 500, "The exchanger has finite coolant");

        h.setBlock(elbow, CETransport.STAINLESS_PIPE.get().defaultBlockState()
            .setValue(FluidPipeBlock.DOWN, true).setValue(FluidPipeBlock.EAST, true));
        h.setBlock(pump.east(), CETransport.STAINLESS_PIPE.get().defaultBlockState()
            .setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.EAST, true));
        var pumpBlock = BuiltInRegistries.BLOCK.get(CreatePlant.location("stainless_steel_pump"));
        h.setBlock(pump, pumpBlock.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST));
        var cog = pump.south();
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(cog.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((CreativeMotorBlockEntity) h.getBlockEntity(cog.west())).generatedSpeed.setValue(64);

        var receiver = pump.east(6);
        h.setBlock(receiver, CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(PulsationTubeBlock.AXIS, Direction.Axis.X));
        h.runAfterDelay(290, () -> {
            var gas = ThermalControlGameTests.tube(exchanger, 1).drain(500, EXECUTE);
            h.assertTrue(gas.is(CEProcessFluids.WATER_GAS.get()) && gas.getAmount() == 500 && gasifier.fluid(2).isEmpty(),
                "The native stainless pump transfers the whole batch into the working exchanger");
            h.assertTrue(FluidHazards.of(gas).heat() == Grade.Heat.AMBIENT
                && fluid(h, receiver, Direction.WEST).fill(gas, EXECUTE) == 500,
                "Cooling keeps the mixture and amount while allowing storage in the gas receiver");
            h.succeed();
        });
    }

    private static void saveTank(GameTestHelper h, CompoundTag saved, int index, FluidStack contents) {
        var tank = new FluidTank(contents.getAmount());
        tank.setFluid(contents);
        saved.put("Tank" + index, tank.writeToNBT(h.getLevel().registryAccess(), new CompoundTag()));
    }

    @GameTest(template = "test_petrochemical")
    public static void preservedControllerAndLegacyRecovery(GameTestHelper h) {
        var be = build(h, EnumSet.of(Direction.NORTH));
        h.assertTrue(be.rebuild(), "Gasifier forms");
        charge(h, 1, new FluidStack(CEFluids.STEAM.get(), 250));
        var lookup = h.getLevel().registryAccess();
        var saved = be.saveWithoutMetadata(lookup);
        var data = saved.getCompound("Gasifier");
        saveTank(h, data, 0, new FluidStack(CEFluids.OXYGEN_GAS.get(), 900));
        saveTank(h, data, 2, new FluidStack(CEProcessFluids.SYNTHESIS_GAS.get(), 2000));
        saveTank(h, data, 3, new FluidStack(Fluids.WATER, 300));
        saveTank(h, data, 4, new FluidStack(CEProcessFluids.WARM_WATER.get(), 150));
        saveTank(h, data, 5, new FluidStack(Fluids.WATER, 50));
        data.put("HotSlag", CEGasifier.SLAG.toStack().save(lookup));
        be.invalidateStructure(false);
        be.loadWithComponents(saved, lookup);
        h.assertTrue(be.rebuild(), "Saved layout still forms");

        var pos = h.absolutePos(BASE);
        var inlet = fluid(h, NORTH_NOZZLE, Direction.NORTH);
        h.getLevel().destroyBlock(pos, true);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(pos).inflate(1));
        h.assertTrue(drops.size() == 1 && inlet.getTankCapacity(0) == 0, "Controller removal leaves one inventory carrier");
        var stack = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(hand, stack);
        var use = new net.minecraft.world.item.context.UseOnContext(player, hand,
            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.UP, pos, false));
        h.assertTrue(((net.minecraft.world.item.BlockItem) stack.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(use)).consumesAction(),
            "Collected casing can be placed normally");
        var restored = (GasifierBlockEntity) h.getBlockEntity(BASE);
        h.assertTrue(restored.rebuild(), "Reassembled casing restores the controller");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(restored, 120);
        h.assertTrue(restored.status().equals("gas_output") && restored.itemCount(0) == 1 && restored.fluid(1).getAmount() == 250,
            "Old synthesis gas blocks the outlet without changing composition or consuming feed");
        var oldGas = fluid(h, BASE.above(5), Direction.UP).drain(16000, EXECUTE);
        h.assertTrue(oldGas.is(CEProcessFluids.SYNTHESIS_GAS.get()) && oldGas.getAmount() == 2000, "Legacy product is recoverable");
        var oldOxidizer = fluid(h, NORTH_NOZZLE, Direction.NORTH).drain(8000, EXECUTE);
        h.assertTrue(oldOxidizer.is(CEFluids.OXYGEN_GAS.get()) && oldOxidizer.getAmount() == 900
            && fluid(h, NORTH_NOZZLE, Direction.NORTH).getTanks() == 1, "Emptying the legacy oxidizer exposes just the steam inlet");
        h.assertTrue(fluid(h, BASE.west(), Direction.WEST).drain(8000, EXECUTE).getAmount() == 300
            && fluid(h, BASE.east(), Direction.EAST).drain(8000, EXECUTE).getAmount() == 150
            && fluid(h, BASE.south(), Direction.SOUTH).drain(4000, EXECUTE).getAmount() == 50
            && items(h, BASE.north(), Direction.NORTH).extractItem(2, 64, false).is(CEGasifier.SLAG.get()),
            "Saved coolants and pending slag are recoverable");
        ticks(restored, 40);
        h.assertTrue(restored.fluid(2).is(CEProcessFluids.WATER_GAS.get()) && restored.fluid(2).getAmount() == 500,
            "The migrated furnace makes the new gas when the old output is cleared");
        h.succeed();
    }
}
