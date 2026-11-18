package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.compat.display.MachineReadings;
import net.createplant.registry.CECompressor;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CETransport;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class PressureGameTests {
    private static FluidStack water(int amount,double kelvin) { return FluidThermal.at(new FluidStack(Fluids.WATER,amount),kelvin); }
    private static FluidStack nitrogen(int amount) { return FluidThermal.at(new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),293.15); }
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos pos,Block block) { h.setBlock(pos,block);return (FluidTankBlockEntity)h.getBlockEntity(pos); }
    private static void nearEnergy(GameTestHelper h,FluidTankBlockEntity be,double expected) { h.assertTrue(Math.abs(FluidThermal.energy(be.getTankInventory().getFluid())-expected)<1e-6,"Phase change preserves total U"); }
    private static IFluidHandler handler(GameTestHelper h,BlockPos pos) { return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),Direction.DOWN); }


    @GameTest(template="test_petrochemical",timeoutTicks=200)
    public static void aFlowMeterReadsWhatThePumpMovesAndZeroOnceItStops(GameTestHelper h) {
        BlockPos source=new BlockPos(1,2,3),meter=new BlockPos(3,2,3),dest=new BlockPos(4,2,3),motor=new BlockPos(1,2,4);
        tank(h,source,AllBlocks.FLUID_TANK.get());tank(h,dest,AllBlocks.FLUID_TANK.get());
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(meter,CETransport.PIPE_FLOW_METER.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        handler(h,source).fill(water(8000,300),EXECUTE);
        var gauge=(PipeGaugeBlockEntity)h.getBlockEntity(meter);
        double[] summed={0,0};
        h.onEachTick(()->summed[0]+=gauge.reading());
        h.runAfterDelay(5,()->((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.runAfterDelay(40,()-> { summed[0]=0;summed[1]=((FluidTankBlockEntity)h.getBlockEntity(dest)).getTankInventory().getFluidAmount(); });
        h.runAfterDelay(60,()-> {
            double moved=((FluidTankBlockEntity)h.getBlockEntity(dest)).getTankInventory().getFluidAmount()-summed[1];
            h.assertTrue(moved>0 && gauge.reading()>0,"Fixture pumps water through the meter: "+moved);
            h.assertTrue(Math.abs(summed[0]-moved)<=Math.max(moved*.1,gauge.reading()),"The meter's readings add up to what reached the tank: "+summed[0]+" / "+moved);
            h.assertTrue(MachineReadings.read(gauge,"flow_rate")!=null && MachineReadings.read(gauge,"pipe_temperature")==null,"A display link reads the flow and nothing else");
            ((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(0);
        });
        h.runAfterDelay(80,()-> {
            h.assertTrue(gauge.measured() && gauge.reading()==0,"With the pump stopped the meter reads zero: "+gauge.reading());
            h.succeed();
        });
    }
}
