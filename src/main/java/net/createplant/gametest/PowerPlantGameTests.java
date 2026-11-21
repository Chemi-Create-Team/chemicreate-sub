package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.compat.display.MachineReadings;
import net.createplant.fusion.FusionStructure;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerPlantGameTests {
    private static void near(GameTestHelper h, double a, double b, String message) {
        h.assertTrue(Double.isFinite(a) && Math.abs(a-b) <= Math.max(.001, Math.abs(b)*1e-8), message + ": " + a + " / " + b);
    }
    private static FluidStack water(int amount, double kelvin) { return FluidThermal.at(new FluidStack(Fluids.WATER, amount), kelvin); }
    @GameTest(template = "test_empty")
    public static void actuatedValveRetainsItsSharedTickBudgetAfterReload(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, CEHeating.REGULATOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(pos.above(), CEPlantSystems.VALVE_ACTUATOR.get().defaultBlockState().setValue(ProcessControlBlock.FACING, Direction.UP));
        h.setBlock(pos.above().east(), Blocks.REDSTONE_BLOCK);
        ((ProcessControlBlockEntity)h.getBlockEntity(pos.above())).tick();
        var valve = (FlowRegulatorBlockEntity)h.getBlockEntity(pos);valve.consume(100_000);
        int remaining = valve.remaining();var saved = valve.saveWithoutMetadata(h.getLevel().registryAccess());
        valve.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(remaining == 162_144 && valve.remaining() == remaining, "A reload does not reset the high-flow valve's shared same-tick quota");h.succeed();
    }
    @GameTest(template = "test_rbmk")
    public static void fuelCycleRecipesLoadAndMoxIdentitySurvivesStorage(GameTestHelper h) {
        for (String recipe : new String[]{"cutting/spent_fuel_shearing", "pressure_reaction/spent_fuel_reprocessing", "solvent_extraction/purex_partition",
            "pressure_reaction/plutonium_oxide_recovery", "mixing/mox_blending", "pressing/green_mox_pellet", "pressure_reaction/mox_sintering", "mox_fuel_rod", "vitrification/high_level_waste_glass"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location(recipe)).isPresent(), "The complete survival fuel-cycle recipe loads: " + recipe);
        BlockPos base = new BlockPos(5, 4, 5);FuelPoolGameTests.build(h, base, 2, 2);
        var fluid = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(base), Direction.DOWN);
        fluid.fill(water(4 * FuelPoolBlockEntity.WATER_PER_CELL, 293.15), EXECUTE);
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(base), Direction.UP);
        h.assertTrue(items.insertItem(0, new ItemStack(CEFuelCycle.MOX_FUEL_ROD.get()), false).isEmpty(), "The pool accepts MOX assemblies");
        var pool = (FuelPoolBlockEntity)h.getBlockEntity(base);var saved = pool.saveWithoutMetadata(h.getLevel().registryAccess());pool.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(items.getStackInSlot(0).is(CEFuelCycle.MOX_FUEL_ROD.get()), "Pool reload retains MOX identity instead of converting it into uranium fuel");h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void actualDimensionsAndQuantizedPidLoop(GameTestHelper h) {
        h.assertTrue(CoolingTowerStructure.HEIGHT == 36 && CoolingTowerStructure.RADIUS * 2 + 1 == 25, "The cooling tower uses the compact 25 x 25 x 36 layout");
        h.assertTrue(FuelCycleStructure.cells(BlockPos.ZERO, Direction.NORTH, false).stream().mapToInt(c -> c.pos().getY()).max().orElse(0) == 11, "The pulse column is twelve metres high");
        PidLoop loop = new PidLoop();double measurement = 20;
        for (int i = 0; i < 4000; i++) {
            double output = Math.round(loop.step(50, measurement, 2, 10, .5, false, .25) * .15) / .15;
            measurement = Math.clamp(measurement + (output - 50) * .0125, 0, 100);
        }
        h.assertTrue(Math.abs(measurement-50) < 2, "A physical 0-15 output converges to drum level: " + measurement);
        for (int i = 0; i < 1000; i++) loop.step(100, 0, 2, 10, 0, false, .25);
        h.assertTrue(loop.step(0, 100, 2, 10, 0, false, .25) == 0, "A saturated controller recovers immediately without integral windup");h.succeed();
    }

    @GameTest(template = "test_power_plant", timeoutTicks = 300)
    public static void compactTowerConvertsWarmWaterAndPreservesItsInventory(GameTestHelper h) {
        BlockPos centre = h.absolutePos(new BlockPos(20, 1, 20)), control = centre.offset(0, 1, -CoolingTowerStructure.RADIUS);
        for (var cell : CoolingTowerStructure.cells()) if (cell.role() != CoolingTowerStructure.Role.CLEAR)
            h.getLevel().setBlock(centre.offset(cell.offset()), cell.block().defaultBlockState(), 2);
        var tower = (CoolingTowerBlockEntity) h.getLevel().getBlockEntity(control);
        for (int i = 0; i < 200 && !tower.formed(); i++) tower.validateSlice();
        h.assertTrue(tower.formed(), "The compact 25 x 25 x 36 tower forms: " + tower.status());
        for (var cell : CoolingTowerStructure.cells()) {
            if (cell.role() != CoolingTowerStructure.Role.HOT && cell.role() != CoolingTowerStructure.Role.COLD) continue;
            BlockPos at = centre.offset(cell.offset());
            Direction outside = cell.role() == CoolingTowerStructure.Role.HOT ? Direction.NORTH : Direction.SOUTH;
            h.assertTrue(h.getLevel().isEmptyBlock(at.relative(outside)), "Every tower nozzle has an accessible external pipe connection");
            h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, at, outside) != null,
                "Every tower nozzle exposes a standard handler after formation");
        }
        int flow=CoolingTowerBlockEntity.MAX_FLOW;long entered=0,returned=0;
        for(int tick=0;tick<100;tick++){
            entered+=tower.port(0).fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),flow),EXECUTE);tower.process();var product=tower.port(1).drain(Integer.MAX_VALUE,EXECUTE);
            h.assertTrue(product.is(Fluids.WATER),"The tower turns warm water into ordinary water");returned+=product.getAmount();
        }
        h.assertTrue(entered==returned && tower.tank(0).isEmpty(),"A long fixed-grade run preserves all water without evaporation state");
        h.assertTrue(tower.port(0).fill(new FluidStack(Fluids.WATER,1000),EXECUTE)==0,"The warm inlet rejects ordinary water");
        var saved=tower.saveWithoutMetadata(h.getLevel().registryAccess());tower.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(!tower.formed(),"Reload requires fresh complete validation");
        for (int i = 0; i < 200 && !tower.formed(); i++) tower.validateSlice();
        h.getLevel().setBlock(centre.offset(0, CoolingTowerStructure.HEIGHT - 1, 8), Blocks.AIR.defaultBlockState(), 3);
        tower.structureChanged();h.assertTrue(!tower.formed(), "Removing the tower rim stops cooling immediately");h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 140)
    public static void levelLinkDrivesAValveAndSignalLossClosesIt(GameTestHelper h) {
        BlockPos tankPos = new BlockPos(3, 2, 3), sensorPos = tankPos.north(), pidPos = new BlockPos(8, 2, 3), valvePos = new BlockPos(10, 2, 3);
        h.setBlock(tankPos, AllBlocks.FLUID_TANK.get());
        h.setBlock(sensorPos, CEPlantSystems.LEVEL_TRANSMITTER.get().defaultBlockState().setValue(ProcessControlBlock.FACING, Direction.NORTH));
        h.setBlock(pidPos, CEPlantSystems.PID_CONTROLLER.get());h.setBlock(pidPos.above(), CEPlantSystems.PID_RACK.get());
        h.setBlock(valvePos, CEHeating.REGULATOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        h.setBlock(valvePos.above(), CEPlantSystems.VALVE_ACTUATOR.get().defaultBlockState().setValue(ProcessControlBlock.FACING, Direction.UP));
        h.setBlock(valvePos.above().east(), Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(10, () -> {
            var tank = ((FluidTankBlockEntity)h.getBlockEntity(tankPos)).getControllerBE();tank.getTankInventory().fill(water(2000, 293.15), EXECUTE);
            var sensor = (ProcessControlBlockEntity)h.getBlockEntity(sensorPos);sensor.sample();
            h.assertTrue(sensor.valid() && sensor.normalized() > 0 && sensor.normalized() < 50, "The transmitter samples the actual Create tank inventory");
            h.setBlock(sensorPos.north(), AllBlocks.DISPLAY_LINK.getDefaultState().setValue(DisplayLinkBlock.FACING, Direction.NORTH));
            var link = (DisplayLinkBlockEntity)h.getBlockEntity(sensorPos.north());link.activeSource = CEDisplays.MACHINE.get();link.activeTarget = CEDisplays.PID.get();
            link.getSourceConfig().putInt("Metric", MachineReadings.KEYS.indexOf("control_measurement"));link.target(h.absolutePos(pidPos));link.updateGatheredData();
        });
        h.runAfterDelay(20, () -> {
            var pid = (ProcessControlBlockEntity)h.getBlockEntity(pidPos);
            h.assertTrue(pid.valid() && pid.output() > 0, "The standard Display Link delivers a live measurement to the two-block PID rack");
            var valve = (FlowRegulatorBlockEntity)h.getBlockEntity(valvePos);
            h.assertTrue(valve.effectiveLimit() == ProcessControlBlockEntity.ACTUATED_FLOW, "A signal of 15 fully opens the actual regulated flow quota");
            h.setBlock(sensorPos.north(), Blocks.AIR);h.setBlock(valvePos.above().east(), Blocks.AIR);
        });
        h.runAfterDelay(80, () -> {
            var pid = (ProcessControlBlockEntity)h.getBlockEntity(pidPos);
            h.assertTrue(!pid.valid() && pid.output() == 0, "Lost measured signals close the controller output");
            h.assertTrue(((FlowRegulatorBlockEntity)h.getBlockEntity(valvePos)).effectiveLimit() == 0, "Signal loss physically closes the actuator valve");h.succeed();
        });
    }

    private static FuelCycleMachineBlockEntity machine(GameTestHelper h, BlockPos pos, boolean melter) {
        BlockPos absolute = h.absolutePos(pos);
        for (var cell : FuelCycleStructure.cells(absolute, Direction.NORTH, melter)) h.getLevel().setBlock(cell.pos(), cell.state(), 2);
        var be = (FuelCycleMachineBlockEntity)h.getLevel().getBlockEntity(absolute);
        h.assertTrue(be.validate(), "The real-size process machine forms");return be;
    }

    @GameTest(template = "test_fractionation")
    public static void extractionSeparatesFourStreamsWithoutSpendingBlockedFeed(GameTestHelper h) {
        var column = machine(h, new BlockPos(5, 2, 5), false);column.setSpeed(64);
        column.tank(0).fill(new FluidStack(CEFuelCycle.DISSOLVED_FUEL.get(), 8000), EXECUTE);
        column.tank(1).fill(new FluidStack(CEFuelCycle.EXTRACTION_SOLVENT.get(), 2000), EXECUTE);
        column.tank(4).fill(new FluidStack(CEFuelCycle.HIGH_LEVEL_WASTE.get(), FuelCycleMachineBlockEntity.CAPACITY), EXECUTE);
        for (int i = 0; i < 500; i++) column.process();
        h.assertTrue(column.tank(0).getFluidAmount() == 8000 && column.progress() == 0, "A full waste outlet does not consume or reserve feed");
        column.tank(4).drain(Integer.MAX_VALUE, EXECUTE);
        for (int i = 0; i < 200; i++) column.process();
        var saved = column.saveWithoutMetadata(h.getLevel().registryAccess());column.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(column.validate(), "Reloaded pulse column validates");column.setSpeed(64);
        for (int i = 0; i < 200; i++) column.process();
        h.assertTrue(column.tank(0).getFluidAmount() == 4000 && column.tank(1).getFluidAmount() == 1000, "Exactly one saved batch is consumed");
        h.assertTrue(column.tank(2).getFluidAmount() == 3500 && column.tank(3).getFluidAmount() == 100 && column.tank(4).getFluidAmount() == 400 && column.tank(5).getFluidAmount() == 1000, "U/Pu/waste/solvent outputs balance the five-litre process batch");
        var outlet = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, column.getBlockPos().south(2).east().above(9), Direction.EAST);
        h.assertTrue(outlet != null && outlet.drain(1000, SIMULATE).is(CEFuelCycle.EXTRACTION_SOLVENT.get()), "The upper nozzle exposes recycled solvent through a standard Create-compatible handler");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void electricMelterCastsOneSealedCanisterAndPreservesHeat(GameTestHelper h) {
        var melter = machine(h, new BlockPos(6, 2, 5), true);
        var saved = melter.saveWithoutMetadata(h.getLevel().registryAccess());
        melter.loadWithComponents(saved, h.getLevel().registryAccess());melter.validate();
        melter.tank(0).fill(new FluidStack(CEFuelCycle.HIGH_LEVEL_WASTE.get(), 1600), EXECUTE);
        melter.inventory().insertItem(0, new ItemStack(CEFuelCycle.GLASS_FRIT.get(), 4), false);
        melter.inventory().insertItem(1, new ItemStack(CEFuelCycle.EMPTY_CANISTER.get()), false);
        for (int i = 0; i < 1250; i++) { melter.setVoltageSquared(144_000_000);melter.tick(); }
        h.assertTrue(melter.inventory().getStackInSlot(2).is(CEFuelCycle.WASTE_CANISTER.get().asItem()) && melter.inventory().getStackInSlot(2).getCount() == 1, "A powered refractory melter seals exactly one canister");
        h.assertTrue(melter.tank(0).isEmpty() && melter.inventory().getStackInSlot(0).isEmpty() && melter.inventory().getStackInSlot(1).isEmpty(), "Waste, glass frit and empty canister are consumed atomically");
        saved = melter.saveWithoutMetadata(h.getLevel().registryAccess());melter.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(!melter.saveWithoutMetadata(h.getLevel().registryAccess()).contains("Kelvin") && melter.inventory().getStackInSlot(2).getCount()==1,"Reload preserves the sealed canister without a temperature state");h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void condenserConvertsFiniteCoolingWaterOnlyWhenItsWarmOutletHasSpace(GameTestHelper h) {
        var low=TurbineThermalGameTests.build(h.getLevel(),h.absolutePos(BlockPos.ZERO))[1];var box=low.getBlockPos().south(3);
        for(var cell:CondenserWaterboxBlockEntity.cells(box,Direction.NORTH))h.getLevel().setBlock(cell.pos(),cell.state(),2);
        var condenser=(CondenserWaterboxBlockEntity)h.getLevel().getBlockEntity(box);condenser.tick();h.assertTrue(condenser.formed() && condenser.rotor()==low,"The waterbox joins the radial LP casing");
        var state=new net.minecraft.nbt.CompoundTag();low.thermodynamics().write(state,h.getLevel().registryAccess());state.putDouble("RotorPendingCondenserJoules",1000000);low.thermodynamics().read(state,h.getLevel().registryAccess());
        condenser.cold().fill(new FluidStack(Fluids.WATER,1000),EXECUTE);condenser.hot().fill(new FluidStack(CEProcessFluids.WARM_WATER.get(),CondenserWaterboxBlockEntity.CAPACITY),EXECUTE);condenser.tick();
        near(h,low.thermodynamics().pendingCondenserJoules(),1000000,"A blocked warm outlet consumes no condenser budget");h.assertTrue(condenser.cold().getFluidAmount()==1000,"A blocked outlet keeps all cooling water");
        condenser.hot().drain(1000,EXECUTE);condenser.tick();
        h.assertTrue(condenser.cold().isEmpty() && condenser.hot().getFluid().is(CEProcessFluids.WARM_WATER.get()) && condenser.hot().getFluidAmount()==CondenserWaterboxBlockEntity.CAPACITY,"Freeing output space converts exactly 1000 water into warm water");
        near(h,low.thermodynamics().pendingCondenserJoules(),980000,"The fixed cooling-water work is debited exactly once");h.succeed();
    }

    @GameTest(template = "test_fractionation", timeoutTicks = 220)
    public static void condenserCoolingUsesAccessibleNativePumpPorts(GameTestHelper h) {
        var level = h.getLevel();var low = TurbineThermalGameTests.build(level, h.absolutePos(BlockPos.ZERO))[1];
        BlockPos box = low.getBlockPos().south(3);
        for (var cell : CondenserWaterboxBlockEntity.cells(box, Direction.NORTH)) level.setBlock(cell.pos(), cell.state(), 2);
        var condenser = (CondenserWaterboxBlockEntity)level.getBlockEntity(box);condenser.tick();
        BlockPos feedTank = box.west(5), productTank = box.east(5), feedPump = box.west(3), productPump = box.east(3);
        for (BlockPos pos : new BlockPos[]{feedTank, productTank}) level.setBlock(pos, AllBlocks.FLUID_TANK.getDefaultState(), 3);
        for (int offset : new int[]{-4, -2, 2, 4}) level.setBlock(box.east(offset), AllBlocks.FLUID_PIPE.getDefaultState(), 3);
        for (BlockPos pump : new BlockPos[]{feedPump, productPump}) {
            level.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), 3);
            level.setBlock(pump.above(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
            level.setBlock(pump.above().east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST), 3);
            ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)level.getBlockEntity(pump.above().east())).generatedSpeed.setValue(128);
        }
        ((FluidTankBlockEntity)level.getBlockEntity(feedTank)).getTankInventory().fill(water(8000, 285.15), EXECUTE);
        var pending=new net.minecraft.nbt.CompoundTag();low.thermodynamics().write(pending,level.registryAccess());pending.putDouble("RotorPendingCondenserJoules",1000000);low.thermodynamics().read(pending,level.registryAccess());
        h.runAfterDelay(180, () -> {
            var feed = ((FluidTankBlockEntity)level.getBlockEntity(feedTank)).getControllerBE().getTankInventory();
            var product = ((FluidTankBlockEntity)level.getBlockEntity(productTank)).getControllerBE().getTankInventory();
            h.assertTrue(product.getFluidAmount() > 0 && product.getFluid().is(CEProcessFluids.WARM_WATER.get()),
                "Actual Create pipes and pumps carry coolant through the side waterboxes and extract warmed water");
            h.assertTrue(feed.getFluidAmount() + product.getFluidAmount() + condenser.cold().getFluidAmount() + condenser.hot().getFluidAmount() == 8000,
                "The secondary circuit conserves every mB through the two native pumps");h.succeed();
        });
    }

    @GameTest(template = "test_fusion", timeoutTicks = 700)
    public static void craneExchangesWornFusionComponentsThroughTheServiceHatch(GameTestHelper h) {
        BlockPos control = h.absolutePos(new BlockPos(40, 7, 25));
        for (var cell : FusionStructure.cells(control, Direction.SOUTH)) if (cell.role() != FusionStructure.Role.CLEAR) h.getLevel().setBlock(cell.pos(), cell.expected(), 2);
        var reactor = (FusionReactorControllerBlockEntity)h.getLevel().getBlockEntity(control);reactor.tick();
        ItemStack blanket = new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), divertor = new ItemStack(CEFusion.DIVERTOR_CASSETTE.get());
        blanket.setDamageValue(12000);divertor.setDamageValue(8000);reactor.blanketPort().insertItem(0, blanket, false);reactor.blanketPort().insertItem(1, divertor, false);
        // The crane builds its own frame: legs of sixteen girders down to the floor and two rails of fifteen.
        BlockPos drive = new BlockPos(32, 18, 22);
        var crane = fusionCrane(h, drive);
        ItemHandlerHelper.insertItem(crane.store(), AllBlocks.METAL_GIRDER.asStack(4 * 16 + 2 * 15), false);
        ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        h.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(drive.west())).generatedSpeed.setValue(256));
        h.succeedWhen(() -> h.assertTrue(reactor.componentRemaining(0) == 24000 && reactor.componentRemaining(1) == 16000,
            "The travelling crane exchanges both worn components: " + crane.status() + ", " + reactor.componentRemaining(0) + ", " + reactor.componentRemaining(1)));
    }

    /** A fusion crane east of a 15 × 13 range, turned from the west and running. */
    private static CraneDriveBlockEntity fusionCrane(GameTestHelper h, BlockPos drive) {
        h.setBlock(drive, CECrane.FUSION_DRIVE.get().defaultBlockState().setValue(CraneDriveBlock.FACING, Direction.EAST).setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.setBlock(drive.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));h.setBlock(drive.above(), Blocks.REDSTONE_BLOCK);
        var crane = (CraneDriveBlockEntity)h.getBlockEntity(drive);
        crane.size(15, 13);
        return crane;
    }

    @GameTest(template = "test_fusion", timeoutTicks = 700)
    public static void craneSwapsAUsedUpIrradiationCartridgeForItsYield(GameTestHelper h) {
        BlockPos control = h.absolutePos(new BlockPos(40, 7, 25));
        for (var cell : FusionStructure.cells(control, Direction.SOUTH)) if (cell.role() != FusionStructure.Role.CLEAR) h.getLevel().setBlock(cell.pos(), cell.expected(), 2);
        var reactor = (FusionReactorControllerBlockEntity)h.getLevel().getBlockEntity(control);reactor.tick();
        ItemStack used = new ItemStack(CEFusion.IRRADIATION_CARTRIDGE.get());used.setDamageValue(used.getMaxDamage());
        reactor.blanketPort().insertItem(0, used, false);reactor.blanketPort().insertItem(1, new ItemStack(CEFusion.DIVERTOR_CASSETTE.get()), false);
        h.assertTrue(!reactor.breedsTritium(), "An irradiation cartridge in the blanket breeds no tritium");
        // A frame built by hand is accepted as well: girder legs standing on the floor under girder rails.
        int railY = 18;
        for (int x : new int[]{33, 47}) for (int z : new int[]{22, 36}) for (int y = 2; y < railY; y++) h.setBlock(new BlockPos(x, y, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)), Direction.Axis.Y));
        for (int z : new int[]{22, 36}) for (int x = 33; x <= 47; x++) h.setBlock(new BlockPos(x, railY, z), CraneFrame.girder(h.getLevel(), h.absolutePos(new BlockPos(x, railY, z)), Direction.Axis.X));
        var crane = fusionCrane(h, new BlockPos(32, railY, 22));
        BlockPos drive = new BlockPos(32, railY, 22);
        ItemHandlerHelper.insertItem(crane.store(), new ItemStack(CEFusion.LITHIUM_LEAD_BLANKET.get()), false);
        h.runAfterDelay(5, () -> ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(drive.west())).generatedSpeed.setValue(256));
        h.succeedWhen(() -> {
            int irradiated = 0;
            for (int slot = 0; slot < crane.store().getSlots(); slot++) if (crane.store().getStackInSlot(slot).is(CEFusion.IRRADIATED_CARTRIDGE.get())) irradiated++;
            h.assertTrue(reactor.componentRemaining(0) == 24000 && reactor.breedsTritium() && irradiated == 1,
                "The used-up cartridge comes out irradiated and lithium-lead breeds again: " + crane.status() + ", " + reactor.componentRemaining(0) + ", " + irradiated);
        });
    }
}
