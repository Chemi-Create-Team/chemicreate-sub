package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.CreatePlant;
import net.createplant.compat.display.MachineReadings;
import net.createplant.block.GasifierBlock;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.*;
import net.createplant.thermal.Grade;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GasifierGameTests {
    private static final BlockPos BASE = new BlockPos(5, 2, 5);
    private static final BlockPos INLET = BASE.north(), TOP = BASE.above(5), OUTLET = TOP.north();

    private static GasifierBlockEntity build(GameTestHelper h) {
        for (int y = 0; y < 6; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            h.setBlock(BASE.offset(x, y, z), CEGasifier.GASIFIER.get().defaultBlockState());
        return (GasifierBlockEntity) h.getBlockEntity(BASE);
    }

    private static IFluidHandler fluid(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static IItemHandler items(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static int cell(GameTestHelper h, BlockPos pos) { return h.getBlockState(pos).getValue(GasifierBlock.CELL); }

    private static void charge(GameTestHelper h, int coal, FluidStack steam) {
        h.assertTrue(items(h, TOP.east(), Direction.UP).insertItem(0, new ItemStack(Items.COAL, coal), false).isEmpty(),
            "The roof accepts coal from item automation");
        h.assertTrue(fluid(h, INLET, Direction.NORTH).fill(steam, EXECUTE) == steam.getAmount(),
            "The base inlet accepts the supplied steam");
    }

    private static void burner(GameTestHelper h, BlazeBurnerBlock.HeatLevel heat) {
        h.setBlock(BASE.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
    }

    private static void ticks(GasifierBlockEntity gasifier, int count) {
        for (int tick = 0; tick < count; tick++) gasifier.tick();
    }

    /** Ticks while the furnace body warms to its reaction temperature; the tick that reaches it also runs the batch once. */
    private static void heatUp(GameTestHelper h, GasifierBlockEntity gasifier) {
        for (int tick = 0; tick < 2000; tick++) {
            gasifier.tick();
            if (!gasifier.status().equals("preheat")) return;
        }
        h.fail("The furnace never reaches its reaction temperature: " + gasifier.temperature());
    }

    static FluidStack makeWaterGas(GameTestHelper h, int batches) {
        var gasifier = build(h);
        h.assertTrue(gasifier.rebuild(), "The 3x3x6 gasifier forms before feeding the synthesis line");
        charge(h, batches, new FluidStack(CEFluids.STEAM.get(), 250 * batches));
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        for (int tick = 0; tick < 4000 && gasifier.fluid(2).getAmount() < 600 * batches; tick++) gasifier.tick();
        return fluid(h, OUTLET, Direction.UP).drain(600 * batches + 1, EXECUTE);
    }

    @GameTest(template = "test_petrochemical")
    public static void formationLooksAndPorts(GameTestHelper h) {
        var be = build(h);
        h.setBlock(TOP, Blocks.AIR);
        h.assertTrue(!be.rebuild() && cell(h, BASE) == 0, "53 blocks do not form a furnace");
        h.setBlock(TOP, CEGasifier.GASIFIER.get().defaultBlockState().setValue(GasifierBlock.FACING, Direction.NORTH));
        GasifierBlockEntity.formAround(h.getLevel(), h.absolutePos(TOP));
        h.assertTrue(be.formed() && be.front() == Direction.NORTH, "Placing the last block forms the furnace with the roof centre's front");
        h.assertTrue(cell(h, BASE) == GasifierBlock.CONTROLLER && cell(h, OUTLET) == GasifierBlock.cell(5, 0, -1)
            && cell(h, BASE.above(2).east()) == GasifierBlock.cell(2, 1, 0) && GasifierBlock.viewport(cell(h, BASE.above(2).east()))
            && !h.getBlockState(BASE.above(2).east()).getValue(GasifierBlock.LIT), "Each block shows its place, the cold sight glasses unlit");
        h.assertTrue(fluid(h, INLET, Direction.SOUTH) == null && fluid(h, BASE.above(2).north(), Direction.NORTH) == null
            && fluid(h, TOP, Direction.UP) == null && fluid(h, BASE.north().east(), Direction.NORTH) == null,
            "Only the base inlets and the outlet have fluid capabilities");
        var gas = fluid(h, OUTLET, Direction.UP);
        h.assertTrue(gas.getTankCapacity(0) == 16000 && gas.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 250), EXECUTE) == 0,
            "The front of the roof is a 16000 mB output only");
        var input = fluid(h, INLET, Direction.NORTH);
        h.assertTrue(input.fill(new FluidStack(Fluids.WATER, 250), EXECUTE) == 0
            && fluid(h, BASE.west(), Direction.WEST).fill(new FluidStack(CEFluids.STEAM.get(), 250), EXECUTE) == 250,
            "Every base inlet takes steam and rejects liquid water");
        var feed = items(h, TOP.south().west(), Direction.UP);
        h.assertTrue(feed.insertItem(0, new ItemStack(Items.CHARCOAL), true).isEmpty() && feed.insertItem(0, CEItems.CARBON_POWDER.toStack(), true).isEmpty()
            && feed.insertItem(0, new ItemStack(Items.IRON_INGOT), true).getCount() == 1 && items(h, TOP.east(), Direction.EAST) == null,
            "The roof takes coal, charcoal and carbon powder from above only");
        var pipePos = INLET.north();
        h.setBlock(pipePos, AllBlocks.FLUID_PIPE.getDefaultState());
        h.assertTrue(h.getBlockState(pipePos).getValue(FluidPipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH)), "Create's placed pipe connects toward the inlet");
        h.setBlock(BASE.above(4), Blocks.STONE);
        h.assertTrue(!be.formed() && !be.rebuild() && input.getTankCapacity(0) == 0 && cell(h, BASE) == 0 && cell(h, OUTLET) == 0,
            "A removed block unforms the furnace, revokes old ports and returns the others to loose blocks");
        h.setBlock(BASE.above(4), CEGasifier.GASIFIER.get().defaultBlockState());
        h.assertTrue(be.rebuild() && input.getTankCapacity(0) == 0, "Reformation cannot revive an invalidated handler");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void retiredPartsLoadAsTheGasifier(GameTestHelper h) {
        var lookup = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        for (var name : java.util.List.of("gasifier_casing", "water_cooled_refractory_wall", "entrained_burner_nozzle", "slag_tap_hearth", "quench_sight_glass")) {
            var id = CreatePlant.location(name);
            var saved = new CompoundTag();
            saved.putString("Name", id.toString());
            var properties = new CompoundTag();
            properties.putString("facing", "east");
            saved.put("Properties", properties);
            var state = net.minecraft.nbt.NbtUtils.readBlockState(lookup, saved);
            h.assertTrue(BuiltInRegistries.BLOCK.get(id) == CEGasifier.GASIFIER.get() && BuiltInRegistries.ITEM.get(id) == CEGasifier.GASIFIER.get().asItem()
                && state.is(CEGasifier.GASIFIER.get()) && state.getValue(GasifierBlock.CELL) == 0 && state.getValue(GasifierBlock.FACING) == Direction.EAST,
                "A saved " + name + " loads as a loose gasifier block");
        }
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void externalHeatAndExactBatch(GameTestHelper h) {
        var be = build(h);
        h.assertTrue(be.rebuild(), "Gasifier forms");
        var inlet = fluid(h, INLET, Direction.NORTH);
        var steam = new FluidStack(CEFluids.STEAM.get(), 9000);
        h.assertTrue(inlet.getTanks() == 1 && inlet.fill(steam, SIMULATE) == 8000 && inlet.getFluidInTank(0).isEmpty(),
            "The steam tank holds 8000 mB and simulation leaves it empty");
        h.assertTrue(inlet.fill(new FluidStack(CEFluids.OXYGEN_GAS.get(), 250), EXECUTE) == 0
                && inlet.fill(new FluidStack(CEProcessFluids.COMPRESSED_AIR.get(), 250), EXECUTE) == 0,
            "The inlet rejects both former oxidizers");
        charge(h, 2, steam.copyWithAmount(500));
        h.assertTrue(inlet.drain(250, SIMULATE).isEmpty() && fluid(h, BASE.west(), Direction.WEST).drain(250, SIMULATE).isEmpty(),
            "A fresh gasifier gives nothing back through its inlets");
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        ticks(be, 75);
        h.assertTrue(be.status().equals("heat_required") && be.temperature() == 20 && be.itemCount(0) == 2,
            "A smouldering burner leaves the cold inventory untouched");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        ticks(be, 32);
        h.assertTrue(be.temperature() < 800 && be.status().equals("preheat") && be.progress() == 0,
            "An active burner preheats the empty chamber without committing feed");
        heatUp(h, be);
        ticks(be, 38);
        h.assertTrue(be.fluid(2).isEmpty() && h.getBlockState(BASE.above(3).south()).getValue(GasifierBlock.LIT),
            "Thirty-nine hot ticks are insufficient, and the red-hot bed lights the sight glasses");
        ticks(be, 1);
        var gas = fluid(h, OUTLET, Direction.UP).drain(600, EXECUTE);
        var phases=net.createplant.thermal.SteamCondensate.separate(gas,293,Integer.MAX_VALUE);
        h.assertTrue(phases.gas().is(CEProcessFluids.WATER_GAS.get()) && phases.gas().getAmount()==500 && phases.water().getAmount()==100
            && FluidHazards.of(gas).heat() == Grade.Heat.HOT
            && FluidHazards.of(gas).gas() && net.createplant.transport.PipeMaterial.COPPER.carries(gas), "The copper outlet delivers 500 mB of hot water gas with 100 mB of steam");
        h.assertTrue(be.itemCount(0) == 1 && inlet.getFluidInTank(0).getAmount() == 250
            && items(h, BASE, Direction.DOWN) == null, "Each batch consumes one coal and 250 mB of steam with no slag");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void lossOfExternalHeatPausesBatch(GameTestHelper h) {
        var be = build(h);
        h.assertTrue(be.rebuild(), "Gasifier forms");
        charge(h, 1, new FluidStack(CEProcessFluids.SUPERHEATED_STEAM.get(), 250));
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        heatUp(h, be);
        ticks(be, 12);
        double progress = be.progress();
        h.assertTrue(progress == 13 && be.temperature() >= 800, "The burner has formed a partial batch: " + progress + " at " + be.temperature());
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        ticks(be, 30);
        h.assertTrue(be.status().equals("heat_required") && be.progress() == progress
            && be.itemCount(0) == 1 && be.fluid(1).getAmount() == 250 && be.fluid(2).isEmpty(),
            "A hot chamber still pauses immediately when its external heat stops");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        heatUp(h, be);
        ticks(be, 26);
        h.assertTrue(be.fluid(2).getAmount() == 600 && be.itemCount(0) == 0 && be.fluid(1).isEmpty(),
            "Restoring heat completes the same batch exactly once");
        h.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void blockedOutputPreservesFeed(GameTestHelper h) {
        var be = build(h);
        h.assertTrue(be.rebuild(), "Gasifier forms");
        charge(h, 33, new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 8000));
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        for (int tick = 0; tick < 12000 && !be.status().equals("gas_output"); tick++) be.tick();
        fluid(h, INLET, Direction.NORTH).fill(new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 250), EXECUTE);
        ticks(be, 40);
        h.assertTrue(be.status().equals("gas_output") && be.fluid(2).getAmount() == 15600
            && be.itemCount(0) == 7 && be.fluid(1).getAmount() == 1750, "A full tank pauses with feed intact");
        fluid(h, OUTLET, Direction.UP).drain(600, EXECUTE);
        ticks(be, 40);
        h.assertTrue(be.fluid(2).getAmount() == 15600 && be.itemCount(0) == 6 && be.fluid(1).getAmount() == 1500,
            "Freeing one batch of space restarts production");
        h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 840)
    public static void pumpedOutputCanBeCooledAndStored(GameTestHelper h) {
        var gasifier = build(h);
        h.assertTrue(gasifier.rebuild(), "Gasifier forms");
        charge(h, 1, new FluidStack(CEFluids.STEAM.get(), 250));
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        var furnace = BASE.below();
        h.assertTrue(BlazeBurnerBlock.tryInsert(h.getBlockState(furnace), h.getLevel(), h.absolutePos(furnace),
            Items.COAL.getDefaultInstance(), false, false, false).getResult().consumesAction(), "The external burner lights with ordinary coal");

        var elbow = OUTLET.above();
        var pump = elbow.east();
        var exchanger = ThermalControlGameTests.build(h.getLevel(), h.absolutePos(pump.east(2)), Direction.NORTH, 2);
        var receivingTube = ThermalControlGameTests.shell(exchanger, 1);
        h.assertTrue(receivingTube.fill(new FluidStack(Fluids.WATER, 600), EXECUTE) == 600, "The exchanger has finite coolant");

        h.setBlock(elbow, AllBlocks.FLUID_PIPE.getDefaultState()
            .setValue(FluidPipeBlock.DOWN, true).setValue(FluidPipeBlock.EAST, true));
        h.setBlock(pump.east(), AllBlocks.FLUID_PIPE.getDefaultState()
            .setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.EAST, true));
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        var cog = pump.south();
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(cog.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((CreativeMotorBlockEntity) h.getBlockEntity(cog.west())).generatedSpeed.setValue(64);

        var receiver=pump.east(7);
        int length=net.createplant.block.GasTankContainerBlock.length(1);
        var container=CETransport.GAS_TANK_CONTAINER.get().defaultBlockState().setValue(net.createplant.block.GasTankContainerBlock.AXIS,Direction.Axis.X);
        for(int x=0;x<length;x++)h.setBlock(receiver.east(x),container);
        for(int x:new int[]{4,6})h.setBlock(pump.east(x),AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.WEST,true).setValue(FluidPipeBlock.EAST,true));
        var second=pump.east(5);
        h.setBlock(second,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING,Direction.EAST));
        h.setBlock(second.south(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(second.south().west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        ((CreativeMotorBlockEntity)h.getBlockEntity(second.south().west())).generatedSpeed.setValue(64);
        h.runAfterDelay(5, () -> {
            var maximum = MachineReadings.read(gasifier, "production_fluid_max");
            h.assertTrue(maximum != null && maximum.value() == 300 && MachineReadings.productionBottleneck(gasifier).getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents reason && reason.getKey().endsWith(".heat"),
                "A preheating gasifier shows the single recipe's 300 mB/s wet-gas rating and the heating bottleneck");
        });
        // One lit burner warms the furnace body to its reaction temperature in about twelve seconds.
        h.runAfterDelay(330, () -> {
            var current = MachineReadings.read(gasifier, "production_fluid");
            h.assertTrue(current != null && current.value() > 0, "The rolling meter counts the committed gasification batch");
        });
        h.runAfterDelay(810, () -> {
            var current = MachineReadings.read(gasifier, "production_fluid");
            h.assertTrue(current != null && current.value() == 0, "The old batch ages out of the rolling window");
            var water=fluid(h,receiver,Direction.DOWN).drain(new FluidStack(Fluids.WATER,600),EXECUTE);
            var gas=fluid(h,receiver,Direction.WEST).getFluidInTank(0);
            h.assertTrue(water.is(Fluids.WATER) && water.getAmount()==100,"The second Create pump sends the condensed water into the tank sump: "+water);
            h.assertTrue(gas.is(CEProcessFluids.WATER_GAS.get()) && gas.getAmount()==500 && gasifier.fluid(2).isEmpty()
                && FluidHazards.of(gas).heat()==Grade.Heat.AMBIENT,"The same pump sends the full dry gas volume without mixing the water back in: "+gas);
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 640)
    public static void pipedGasSuperheatsABlazeBurner(GameTestHelper h) {
        var gasifier = build(h);
        h.assertTrue(gasifier.rebuild(), "Gasifier forms");
        charge(h, 2, new FluidStack(CEFluids.STEAM.get(), 500));
        burner(h, BlazeBurnerBlock.HeatLevel.SMOULDERING);
        h.assertTrue(BlazeBurnerBlock.tryInsert(h.getBlockState(BASE.below()), h.getLevel(), h.absolutePos(BASE.below()),
            Items.COAL.getDefaultInstance(), false, false, false).getResult().consumesAction(), "The furnace's own burner lights with coal");
        var elbow = OUTLET.above();
        var pump = elbow.east();
        var fed = pump.east(2);
        h.setBlock(fed, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        h.setBlock(elbow, AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.DOWN, true).setValue(FluidPipeBlock.EAST, true));
        h.setBlock(pump.east(), AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.WEST, true).setValue(FluidPipeBlock.EAST, true));
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        var cog = pump.south();
        h.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(cog.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        ((CreativeMotorBlockEntity) h.getBlockEntity(cog.west())).generatedSpeed.setValue(64);
        h.assertTrue(fluid(h, fed, Direction.DOWN) != null && fluid(h, fed, Direction.UP) == null,
            "The burner takes the gas through its sides and its bottom, but not its top");
        h.runAfterDelay(610, () -> {
            h.assertTrue(gasifier.fluid(2).isEmpty() && gasifier.itemCount(0) == 0, "Both batches left the furnace");
            h.assertTrue(h.getBlockState(fed).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SEETHING,
                "1000 mB pumped through Create's copper pipe superheats the burner: " + h.getBlockState(fed).getValue(BlazeBurnerBlock.HEAT_LEVEL));
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
        var be = build(h);
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
        var inlet = fluid(h, INLET, Direction.NORTH);
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
            "The collected block can be placed normally");
        var restored = (GasifierBlockEntity) h.getBlockEntity(BASE);
        h.assertTrue(restored.rebuild(), "The reassembled bottom centre restores the controller");
        burner(h, BlazeBurnerBlock.HeatLevel.KINDLED);
        heatUp(h, restored);
        h.assertTrue(restored.status().equals("gas_output") && restored.itemCount(0) == 1 && restored.fluid(1).getAmount() == 250,
            "Old synthesis gas blocks the outlet without changing composition or consuming feed");
        var oldGas = fluid(h, OUTLET, Direction.UP).drain(16000, EXECUTE);
        h.assertTrue(oldGas.is(CEProcessFluids.SYNTHESIS_GAS.get()) && oldGas.getAmount() == 2000, "Legacy product is recoverable");
        var oldOxidizer = fluid(h, INLET, Direction.NORTH).drain(8000, EXECUTE);
        h.assertTrue(oldOxidizer.is(CEFluids.OXYGEN_GAS.get()) && oldOxidizer.getAmount() == 900
            && fluid(h, INLET, Direction.NORTH).getTanks() == 1, "Emptying the legacy oxidizer exposes just the steam inlet");
        h.assertTrue(fluid(h, BASE.west(), Direction.WEST).drain(8000, EXECUTE).getAmount() == 300
            && fluid(h, BASE.east(), Direction.EAST).drain(8000, EXECUTE).getAmount() == 150
            && fluid(h, BASE.south(), Direction.SOUTH).drain(4000, EXECUTE).getAmount() == 50
            && items(h, BASE.north(), Direction.NORTH).extractItem(2, 64, false).is(CEGasifier.SLAG.get()),
            "Saved coolants and pending slag are recoverable");
        ticks(restored, 40);
        h.assertTrue(net.createplant.thermal.SteamCondensate.vapour(restored.fluid(2)) && restored.fluid(2).getAmount() == 600,
            "The migrated furnace makes the new gas when the old output is cleared");
        h.succeed();
    }
}
