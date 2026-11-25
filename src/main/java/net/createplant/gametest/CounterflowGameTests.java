package net.createplant.gametest;

import net.createplant.thermal.FluidForm;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import net.createplant.CreatePlant;
import net.createplant.block.ExchangerStructure;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.createplant.thermal.FluidState;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class CounterflowGameTests {
    private static void run(ReactorMachineBlockEntity be){for(int i=0;i<5;i++)be.tickProcess();}
    private static void arrangement(GameTestHelper h,Direction face){
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(8,3,8)),face,3);
        int[] result=new int[2];
        for(int counter=0;counter<2;counter++){
            ThermalControlGameTests.tube(be,0).fill(new FluidStack(Fluids.WATER,3000),EXECUTE);
            ThermalControlGameTests.shell(be,counter).fill(FluidForm.HOT_SYNTHESIS_MIXTURE.stack(3000),EXECUTE);
            h.assertTrue(be.thermal().counterflow()==(counter==1),"The actual inlet positions select counterflow in "+face);
            run(be);result[counter]=be.thermal().tank(3).getFluidAmount();
            var water=be.thermal().tank(1).getFluid();var mixture=be.thermal().tank(3).getFluid();
            // A gas mB holds about a thousandth of a water mB's heat: the gas cools to the water's temperature and the water barely warms.
            h.assertTrue(water.is(Fluids.WATER) && FluidState.temperature(water)==FluidState.AMBIENT && mixture.is(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get())
                && FluidState.temperature(mixture)==FluidState.AMBIENT,"The gas cools to the water and the water keeps its level: "
                +FluidState.temperature(water)+"/"+FluidState.temperature(mixture));
            for(int i=0;i<4;i++)be.thermal().tank(i).setFluid(FluidStack.EMPTY);
        }
        h.assertTrue(result[0]==300 && result[1]==300,"Both arrangements move the same throughput: "+result[0]+"/"+result[1]);h.succeed();
    }
    @GameTest(template="test_fractionation") public static void northFacingBothArrangements(GameTestHelper h){arrangement(h,Direction.NORTH);}
    @GameTest(template="test_fractionation") public static void eastFacingBothArrangements(GameTestHelper h){arrangement(h,Direction.EAST);}
    @GameTest(template="test_fractionation") public static void southFacingBothArrangements(GameTestHelper h){arrangement(h,Direction.SOUTH);}
    @GameTest(template="test_fractionation") public static void westFacingBothArrangements(GameTestHelper h){arrangement(h,Direction.WEST);}
    @GameTest(template="test_fractionation")
    public static void partialOutputSpaceAndMissingCoolantCannotConsumeAnUnpaidPair(GameTestHelper h){
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,3,6)),Direction.NORTH,2);var heat=be.thermal();
        heat.rowPort(0,0).fill(new FluidStack(Fluids.WATER,10),EXECUTE);run(be);h.assertTrue(heat.tank(0).getFluidAmount()==10 && heat.tank(1).isEmpty(),"A missing second stream does not bypass the recipe");
        // A gas that stays a gas at the water's temperature, so each pair is one mB of each.
        heat.rowPort(1,1).fill(FluidForm.HOT_SYNTHESIS_MIXTURE.stack(10),EXECUTE);heat.tank(1).fill(FluidForm.WARM_WATER.stack(7997),EXECUTE);run(be);
        h.assertTrue(heat.tank(0).getFluidAmount()==7 && heat.tank(2).getFluidAmount()==7 && heat.tank(1).getFluidAmount()==8000 && heat.tank(3).getFluidAmount()==3,"Only three complete pairs fit in the destination");
        var before=be.saveWithoutMetadata(h.getLevel().registryAccess());for(int i=0;i<20;i++)be.tickProcess();
        h.assertTrue(heat.tank(0).getFluidAmount()==7 && heat.tank(2).getFluidAmount()==7,"Blocked output consumes neither side");
        heat.tank(1).drain(7,EXECUTE);run(be);h.assertTrue(heat.tank(0).isEmpty() && heat.tank(2).isEmpty() && heat.tank(3).getFluidAmount()==10,"Making room completes exactly the remaining pairs");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void lengthAndLargeCrossSectionScaleTheNumberOfConversionUnits(GameTestHelper h){
        for(var kind:ExchangerStructure.Kind.values()){
            var at=h.absolutePos(new BlockPos(6+kind.ordinal()*7,3,8));var be=ThermalControlGameTests.exchanger(h.getLevel(),at,Direction.NORTH,3,kind);
            be.thermal().rowPort(0,0).fill(new FluidStack(Fluids.WATER,3000),EXECUTE);be.thermal().rowPort(1,0).fill(FluidForm.HOT_SYNTHESIS_MIXTURE.stack(3000),EXECUTE);run(be);
            h.assertTrue(be.sections()==3*kind.sections && be.thermal().tank(3).getFluidAmount()==300*kind.sections,"Length and the real cross section set throughput for "+kind);
        }h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void convertingFluidTypesKeepsTheirSeparateComponentData(GameTestHelper h){
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,3,6)),Direction.NORTH,2);var heat=be.thermal();
        var hot=FluidForm.HOT_SYNTHESIS_MIXTURE.stack(10);var cold=new FluidStack(Fluids.WATER,10);
        var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Lot",42);hot.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        tag=new net.minecraft.nbt.CompoundTag();tag.putInt("Lot",73);cold.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
        heat.rowPort(0,0).fill(hot,EXECUTE);heat.rowPort(1,1).fill(cold,EXECUTE);run(be);
        h.assertTrue(heat.tank(1).getFluid().is(CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get()) && heat.tank(3).getFluid().is(Fluids.WATER),"Both products are made: "+be.status());
        h.assertTrue(heat.tank(1).getFluid().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("Lot")==42 && heat.tank(3).getFluid().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getInt("Lot")==73,"Changing fluid identity preserves each stream's own components without mixing them");h.succeed();
    }
}
