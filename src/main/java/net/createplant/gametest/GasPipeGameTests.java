package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.PressureValveBlock;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class GasPipeGameTests {
    private static final BlockPos SOURCE=new BlockPos(1,2,3),DEST=new BlockPos(7,2,3);
    private static FluidTankBlockEntity tank(GameTestHelper h,BlockPos at){h.setBlock(at,CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.X));return (FluidTankBlockEntity)h.getBlockEntity(at);}
    private static BlockState pipe(boolean high){return (high ? CETransport.STAINLESS_PIPE.get() : CEGasPipes.GAS_PIPE.get()).defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true);}
    private static CreativeMotorBlockEntity pump(GameTestHelper h,BlockPos at){
        h.setBlock(at,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(at.south(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(at.south().west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        var motor=(CreativeMotorBlockEntity)h.getBlockEntity(at.south().west());motor.generatedSpeed.setValue(0);return motor;
    }
    @GameTest(template="test_petrochemical",timeoutTicks=180)
    public static void gasOnlyMovesWithANativePumpAndConservesTheWholeCharge(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);for(int i=1;i<6;i++)h.setBlock(SOURCE.east(i),pipe(false));
        source.getTankInventory().fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),10000),EXECUTE);
        h.runAfterDelay(30,()-> {h.assertTrue(dest.getTankInventory().isEmpty() && source.getTankInventory().getFluidAmount()==10000,"No passive pressure-difference flow remains");pump(h,SOURCE.east()).generatedSpeed.setValue(64);});
        h.runAfterDelay(120,()-> {h.assertTrue(!dest.getTankInventory().isEmpty(),"The native pump moves gas along gas pipes");h.assertTrue(source.getTankInventory().getFluidAmount()+dest.getTankInventory().getFluidAmount()==10000,"Pipes hold no second inventory and gas is conserved");h.succeed();});
    }
    @GameTest(template="test_petrochemical",timeoutTicks=150)
    public static void incompatibleHighPressureGasStopsAtReinforcedPipeWithoutDamage(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);for(int i=1;i<6;i++)h.setBlock(SOURCE.east(i),pipe(false));
        source.getTankInventory().fill(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),2000),EXECUTE);pump(h,SOURCE.east()).generatedSpeed.setValue(64);
        h.runAfterDelay(100,()-> {h.assertTrue(dest.getTankInventory().isEmpty() && source.getTankInventory().getFluidAmount()==2000,"A pump cannot force an incompatible grade through gas pipe");for(int i=2;i<6;i++)h.assertBlockPresent(CEGasPipes.GAS_PIPE.get(),SOURCE.east(i));h.succeed();});
    }
    @GameTest(template="test_petrochemical",timeoutTicks=180)
    public static void twoNativePumpsMoveGasThroughTheDirectionalReducer(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);
        h.setBlock(SOURCE.east(2),pipe(true));h.setBlock(SOURCE.east(4),pipe(false));
        h.setBlock(SOURCE.east(3),CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING,Direction.EAST).setValue(PressureValveBlock.AXIS,Direction.Axis.X));
        pump(h,SOURCE.east()).generatedSpeed.setValue(64);pump(h,SOURCE.east(5)).generatedSpeed.setValue(64);
        source.getTankInventory().fill(new FluidStack(CEProcessFluids.COMPRESSED_HYDROGEN.get(),2000),EXECUTE);
        h.runAfterDelay(130,()-> {var out=dest.getTankInventory().getFluid();h.assertTrue(out.is(CEProcessFluids.PRESSURISED_HYDROGEN.get()) && out.getAmount()==2000 && source.getTankInventory().isEmpty(),"Native pump -> reducer -> native pump preserves 2000 mB and lowers one grade: "+out.getAmount());h.succeed();});
    }
}
