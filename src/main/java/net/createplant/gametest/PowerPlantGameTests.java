package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.compat.display.MachineReadings;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerPlantGameTests {
    private static void near(GameTestHelper h, double a, double b, String message) {
        h.assertTrue(Double.isFinite(a) && Math.abs(a-b) <= Math.max(.001, Math.abs(b)*1e-8), message + ": " + a + " / " + b);
    }
    static FluidStack water(int amount, double kelvin) { return FluidThermal.at(new FluidStack(Fluids.WATER, amount), kelvin); }
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
    @GameTest(template = "test_power_plant", timeoutTicks = 300)
    public static void compactTowerConvertsWarmWaterAndPreservesItsInventory(GameTestHelper h) {
        BlockPos centre = h.absolutePos(new BlockPos(20, 1, 20)), control = centre.offset(0, 1, -CoolingTowerStructure.RADIUS);
        for (var cell : CoolingTowerStructure.cells()) if (cell.role() != CoolingTowerStructure.Role.CLEAR)
            h.getLevel().setBlock(centre.offset(cell.offset()), cell.block().defaultBlockState(), 2);
        var tower = (CoolingTowerBlockEntity) h.getLevel().getBlockEntity(control);
        for (int i = 0; i < 200 && !tower.formed(); i++) tower.validateSlice();
h.assertTrue(tower.formed(), "The compact 25 x 25 x 36 tower forms");
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
            entered+=tower.port(0).fill(FluidForm.WARM_WATER.stack(flow),EXECUTE);tower.process();var product=tower.port(1).drain(Integer.MAX_VALUE,EXECUTE);
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

    @GameTest(template = "test_fractionation")
    public static void condenserConvertsFiniteCoolingWaterOnlyWhenItsWarmOutletHasSpace(GameTestHelper h) {
        var low=TurbineThermalGameTests.build(h.getLevel(),h.absolutePos(BlockPos.ZERO))[1];var box=low.getBlockPos().south(3);
        for(var cell:CondenserWaterboxBlockEntity.cells(box,Direction.NORTH))h.getLevel().setBlock(cell.pos(),cell.state(),2);
        var condenser=(CondenserWaterboxBlockEntity)h.getLevel().getBlockEntity(box);condenser.tick();h.assertTrue(condenser.formed() && condenser.rotor()==low,"The waterbox joins the radial LP casing");
        var state=new net.minecraft.nbt.CompoundTag();low.thermodynamics().write(state,h.getLevel().registryAccess());state.putDouble("RotorPendingCondenserJoules",1000000);low.thermodynamics().read(state,h.getLevel().registryAccess());
        condenser.cold().fill(new FluidStack(Fluids.WATER,1000),EXECUTE);condenser.hot().fill(FluidForm.WARM_WATER.stack(CondenserWaterboxBlockEntity.CAPACITY),EXECUTE);condenser.tick();
        near(h,low.thermodynamics().pendingCondenserJoules(),1000000,"A blocked warm outlet consumes no condenser budget");h.assertTrue(condenser.cold().getFluidAmount()==1000,"A blocked outlet keeps all cooling water");
        condenser.hot().drain(1000,EXECUTE);condenser.tick();
        h.assertTrue(condenser.cold().isEmpty() && FluidForm.WARM_WATER.matches(condenser.hot().getFluid()) && condenser.hot().getFluidAmount()==CondenserWaterboxBlockEntity.CAPACITY,"Freeing output space converts exactly 1000 water into warm water");
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
            h.assertTrue(product.getFluidAmount() > 0 && FluidForm.WARM_WATER.matches(product.getFluid()),
                "Actual Create pipes and pumps carry coolant through the side waterboxes and extract warmed water");
            h.assertTrue(feed.getFluidAmount() + product.getFluidAmount() + condenser.cold().getFluidAmount() + condenser.hot().getFluidAmount() == 8000,
                "The secondary circuit conserves every mB through the two native pumps");h.succeed();
        });
    }


}
