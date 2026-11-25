package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.CreatePlant;
import net.createplant.block.PressureValveBlock;
import net.createplant.block.entity.PipeGasFlow;
import net.createplant.registry.*;
import net.createplant.thermal.Grade;
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
    private static BlockState pipe(boolean high){return (high ? CETransport.STAINLESS_PIPE.get() : CETransport.NICKEL_ALLOY_PIPE.get()).defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true);}
    private static CreativeMotorBlockEntity pump(GameTestHelper h,BlockPos at){
        h.setBlock(at,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(at.south(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(at.south().west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        var motor=(CreativeMotorBlockEntity)h.getBlockEntity(at.south().west());motor.generatedSpeed.setValue(0);return motor;
    }
    private static PipeGasFlow flow(GameTestHelper h, int offset){
        return BlockEntityBehaviour.get(h.getBlockEntity(SOURCE.east(offset)), PipeGasFlow.TYPE);
    }
    @GameTest(template="test_petrochemical",timeoutTicks=180)
    public static void gasOnlyMovesWithANativePumpAndConservesTheWholeCharge(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);for(int i=1;i<6;i++)h.setBlock(SOURCE.east(i),pipe(false));
        source.getTankInventory().fill(new FluidStack(CEFluids.HYDROGEN_GAS.get(),10000),EXECUTE);
        h.runAfterDelay(30,()-> {h.assertTrue(dest.getTankInventory().isEmpty() && source.getTankInventory().getFluidAmount()==10000,"No passive pressure-difference flow remains");pump(h,SOURCE.east()).generatedSpeed.setValue(64);});
        h.runAfterDelay(120,()-> {h.assertTrue(!dest.getTankInventory().isEmpty(),"The native pump moves gas along gas pipes");h.assertTrue(source.getTankInventory().getFluidAmount()+dest.getTankInventory().getFluidAmount()==10000,"Pipes hold no second inventory and gas is conserved");h.assertTrue(flow(h,3).flowBand()==0,"Ordinary gas is silent while it moves");h.succeed();});
    }
    @GameTest(template="test_petrochemical",timeoutTicks=150)
    public static void highPressureGasKeepsItsGradeAcrossMixedOrdinaryPipes(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);
        h.setBlock(SOURCE.east(2),AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        h.setBlock(SOURCE.east(3),AllBlocks.GLASS_FLUID_PIPE.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(SOURCE.east(4),CETransport.CERAMIC_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        h.setBlock(SOURCE.east(5),CETransport.CERAMIC_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        var gas=FluidForm.COMPRESSED_HYDROGEN.stack(2000);
        source.getTankInventory().fill(gas,EXECUTE);
        pump(h,SOURCE.east()).generatedSpeed.setValue(64);
        h.succeedWhen(()->{
            var received=dest.getTankInventory().getFluid();
            h.assertTrue(received.getAmount()==2000 && FluidStack.isSameFluidSameComponents(received,gas) && source.getTankInventory().isEmpty(),
                "Copper, glass, resin and ceramic pipes carry the whole high-pressure charge unchanged");
        });
    }
    @GameTest(template="test_petrochemical",timeoutTicks=180)
    public static void twoNativePumpsMoveGasThroughTheDirectionalReducer(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);
        h.setBlock(SOURCE.east(2),pipe(true));h.setBlock(SOURCE.east(4),pipe(false));
        h.setBlock(SOURCE.east(3),CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING,Direction.EAST).setValue(PressureValveBlock.AXIS,Direction.Axis.X));
        pump(h,SOURCE.east()).generatedSpeed.setValue(64);pump(h,SOURCE.east(5)).generatedSpeed.setValue(64);
        source.getTankInventory().fill(FluidForm.COMPRESSED_HYDROGEN.stack(2000),EXECUTE);
        h.runAfterDelay(130,()-> {var out=dest.getTankInventory().getFluid();h.assertTrue(FluidForm.PRESSURISED_HYDROGEN.matches(out) && out.getAmount()==2000 && source.getTankInventory().isEmpty(),"Native pump -> reducer -> native pump preserves 2000 mB and lowers one grade: "+out.getAmount());h.succeed();});
    }

    @GameTest(template="test_petrochemical",timeoutTicks=200)
    public static void gasFlowSoundFollowsTwoPressuresAndStopsWhenTheSupplyRunsOut(GameTestHelper h){
        var source=tank(h,SOURCE);var dest=tank(h,DEST);
        h.setBlock(SOURCE.east(2),AllBlocks.FLUID_PIPE.getDefaultState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        h.setBlock(SOURCE.east(4),CETransport.CERAMIC_PIPE.get().defaultBlockState().setValue(FluidPipeBlock.EAST,true).setValue(FluidPipeBlock.WEST,true));
        h.setBlock(SOURCE.east(3),CEPressure.REDUCER.get().defaultBlockState().setValue(PressureValveBlock.FACING,Direction.EAST).setValue(PressureValveBlock.AXIS,Direction.Axis.X));
        var inlet=pump(h,SOURCE.east());var outlet=pump(h,SOURCE.east(5));
        int charge=2048;
        source.getTankInventory().fill(FluidForm.COMPRESSED_HYDROGEN.stack(charge),EXECUTE);
        h.startSequence().thenIdle(24).thenExecute(()->{
            h.assertTrue(flow(h,2).flowBand()==0 && flow(h,4).flowBand()==0,"Stored pressure alone is silent");
            inlet.generatedSpeed.setValue(64);outlet.generatedSpeed.setValue(64);
        }).thenWaitUntil(()->{
            h.assertTrue(flow(h,2).pressureBand()==Grade.Pressure.HIGH.ordinal() && flow(h,4).pressureBand()==Grade.Pressure.PRESSURISED.ordinal(),
                "Gas in motion is heard at its actual grade on either side of the reducer");
            h.assertTrue(flow(h,2).flowBand()>0 && flow(h,4).flowBand()>0,"Both flowing pipes publish an audible band");
            var pipe=h.getBlockEntity(SOURCE.east(2));
            h.assertTrue(pipe.getUpdateTag(h.getLevel().registryAccess()).contains("GasFlow") && !pipe.saveWithoutMetadata(h.getLevel().registryAccess()).contains("GasFlow"),
                "Throughput sync reaches tracking clients and is absent from persisted data");
        }).thenWaitUntil(()->h.assertTrue(dest.getTankInventory().getFluidAmount()==charge && flow(h,2).flowBand()==0 && flow(h,4).flowBand()==0
            && inlet.getSpeed()!=0 && outlet.getSpeed()!=0,"The pipes go silent when no gas moves, with both pumps still spinning")).thenSucceed();
    }
}
