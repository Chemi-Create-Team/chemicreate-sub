package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class FlowControlGameTests {
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorWithCopperAndPartialAcceptance(GameTestHelper h) { regulatedLine(h,AllBlocks.FLUID_PIPE.get()); }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorWithResinAndPartialAcceptance(GameTestHelper h) { regulatedLine(h,CEBlocks.RESIN_PIPE.get()); }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorWithReinforcedAndPartialAcceptance(GameTestHelper h) { regulatedLine(h,CETransport.REINFORCED_PIPE.get()); }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorWithNickelAlloyAndPartialAcceptance(GameTestHelper h) { regulatedLine(h,CETransport.NICKEL_ALLOY_PIPE.get()); }
    private static void regulatedLine(GameTestHelper h,Block pipe) {
        var source=new BlockPos(1,2,3);var target=new BlockPos(6,2,3);var valve=new BlockPos(4,2,3);
        h.setBlock(source,pipe==CETransport.STAINLESS_PIPE.get() ? CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.X) : AllBlocks.FLUID_TANK.getDefaultState());h.setBlock(target,pipe==CETransport.STAINLESS_PIPE.get() ? CETransport.GAS_RECEIVER.get().defaultBlockState().setValue(net.createplant.block.PulsationTubeBlock.AXIS,Direction.Axis.X) : AllBlocks.FLUID_TANK.getDefaultState());
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        for(int x:new int[]{3,5}) {var state=pipe.defaultBlockState();for(var d:Direction.values()) state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),d.getAxis()==Direction.Axis.X);h.setBlock(new BlockPos(x,2,3),state);}
        h.setBlock(valve,CEHeating.REGULATOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        var motor=new BlockPos(1,2,4);h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        var input=((FluidTankBlockEntity)h.getBlockEntity(source)).getTankInventory();var output=((FluidTankBlockEntity)h.getBlockEntity(target)).getTankInventory();
        var fluid=pipe==net.createplant.registry.CETransport.STAINLESS_PIPE.get() ? CEFluids.NITROGEN_GAS.get() : Fluids.WATER;
        input.fill(FluidThermal.at(new FluidStack(fluid,2000),500),EXECUTE);output.fill(FluidThermal.at(new FluidStack(fluid,((net.createplant.thermal.ThermalFluidTank)output).volumeCapacity()/(fluid==CEFluids.NITROGEN_GAS.get() ? 2 : 1)-(fluid==CEFluids.NITROGEN_GAS.get() ? 0 : 17)),500),EXECUTE);int total=input.getFluidAmount()+output.getFluidAmount();
        h.runAfterDelay(5,()->{((FlowRegulatorBlockEntity)h.getBlockEntity(valve)).maximum.setValue(3);((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(256);});
        int[] previous={output.getFluidAmount()};h.onEachTick(()->{int amount=output.getFluidAmount();h.assertTrue(amount-previous[0]<=3,"Actual transfer stays within the shared valve limit on "+pipe);previous[0]=amount;h.assertTrue(input.getFluidAmount()+output.getFluidAmount()==total,"Simulated/partial acceptance conserves volume on "+pipe);});
        h.runAfterDelay(200,()->{h.assertTrue(fluid==CEFluids.NITROGEN_GAS.get() ? input.getFluidAmount()<2000 && input.getFluidAmount()>=1400 : output.getSpace()==0 && input.getFluidAmount()==1983,"Gas retains a finite regulated flow; liquid accepts exactly its last 17 mB on "+pipe);h.succeed();});
    }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorSharesBudgetAcrossBranchLoopAndCanClose(GameTestHelper h) {
        var source=new BlockPos(1,2,3);var a=new BlockPos(6,2,3);var b=new BlockPos(4,2,5);var valve=new BlockPos(3,2,3);
        for(var p:new BlockPos[]{source,a,b}) h.setBlock(p,com.simibubi.create.AllBlocks.FLUID_TANK.get());
        h.setBlock(new BlockPos(2,2,3),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(valve,CEHeating.REGULATOR.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        for(int x=4;x<=5;x++) for(int z=3;z<=4;z++) {
            var p=new BlockPos(x,2,z);var state=com.simibubi.create.AllBlocks.FLUID_PIPE.get().defaultBlockState();
            for(var d:Direction.values()) {var q=p.relative(d);boolean open=q.equals(valve)||q.equals(a)||q.equals(b)||q.getY()==2&&q.getX()>=4&&q.getX()<=5&&q.getZ()>=3&&q.getZ()<=4;state=state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(d),open);}
            h.setBlock(p,state);
        }
        h.setBlock(new BlockPos(2,2,4),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));var motor=new BlockPos(1,2,4);h.setBlock(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
        var input=((FluidTankBlockEntity)h.getBlockEntity(source)).getTankInventory();var first=((FluidTankBlockEntity)h.getBlockEntity(a)).getTankInventory();var second=((FluidTankBlockEntity)h.getBlockEntity(b)).getTankInventory();input.fill(FluidThermal.at(new FluidStack(Fluids.WATER,8000),500),EXECUTE);
        h.runAfterDelay(5,()->{((FlowRegulatorBlockEntity)h.getBlockEntity(valve)).maximum.setValue(3);((CreativeMotorBlockEntity)h.getBlockEntity(motor)).generatedSpeed.setValue(128);});
        int[] previous={0},closed={0},limit={32};h.onEachTick(()->{int transferred=first.getFluidAmount()+second.getFluidAmount();var control=(FlowRegulatorBlockEntity)h.getBlockEntity(valve);h.assertTrue(transferred-previous[0]<=Math.max(limit[0],control.maximum.getValue()),"A shared valve limits the sum of both branches, including the loop: "+(transferred-previous[0])+" / "+control.maximum.getValue());h.assertTrue(input.getFluidAmount()+transferred==8000,"Branch simulation and execution conserve all fluid");previous[0]=transferred;limit[0]=control.maximum.getValue();});
        h.runAfterDelay(80,()->{var control=(FlowRegulatorBlockEntity)h.getBlockEntity(valve);control.maximum.setValue(0);closed[0]=first.getFluidAmount()+second.getFluidAmount();});
        h.runAfterDelay(110,()->{h.assertTrue(previous[0]==closed[0],"Zero flow closes without draining the source");var control=(FlowRegulatorBlockEntity)h.getBlockEntity(valve);var saved=control.saveWithoutMetadata(h.getLevel().registryAccess());control.loadWithComponents(saved,h.getLevel().registryAccess());h.assertTrue(control.maximum.getValue()==0,"Valve setting survives reload");control.maximum.setValue(7);});
        h.runAfterDelay(220,()->{h.assertTrue(first.getFluidAmount()>0&&second.getFluidAmount()>0,"Both branches receive a finite flow after reopening");h.succeed();});
    }
    @GameTest(template="test_petrochemical",timeoutTicks=300)
    public static void regulatorOnAPowerStationGasMainKeepsItsSharedQuota(GameTestHelper h) {regulatedLine(h,CETransport.STAINLESS_PIPE.get());}
}
