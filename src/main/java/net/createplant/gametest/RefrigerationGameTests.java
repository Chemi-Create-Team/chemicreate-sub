package net.createplant.gametest;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.*;
import net.createplant.CreatePlant;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class RefrigerationGameTests {
    @GameTest(template="test_fractionation")
    public static void anExchangerCondensesAWorkingGasOnlyAgainstAFiniteCoolant(GameTestHelper h){
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,3,6)),Direction.NORTH,2);var heat=be.thermal();
        int ratio=PhaseChange.ratio(new FluidStack(CEProcessFluids.LIQUID_AMMONIA.get(),1));
        heat.rowPort(0,0).fill(new FluidStack(CEFluids.AMMONIA_GAS.get(),ratio*3),EXECUTE);for(int i=0;i<10;i++)be.tickProcess();h.assertTrue(heat.tank(1).isEmpty(),"No coolant creates no liquid");
        // Ammonia boils at -33 degrees C, so only a coolant colder than that condenses it; counterflow brings the gas within a level of the coolant.
        heat.rowPort(1,1).fill(FluidState.with(new FluidStack(CEProcessFluids.SLIME_COOLANT.get(),ratio*3),3,FluidState.NORMAL_PRESSURE),EXECUTE);for(int i=0;i<200;i++)be.tickProcess();
        var coolant=heat.tank(3).getFluid();
        h.assertTrue(heat.tank(0).isEmpty() && heat.tank(1).getFluid().is(CEProcessFluids.LIQUID_AMMONIA.get()) && heat.tank(1).getFluidAmount()==3 && heat.tank(2).isEmpty()
            && coolant.is(CEProcessFluids.SLIME_COOLANT.get()) && coolant.getAmount()==ratio*3 && FluidState.temperature(coolant)==7,
            "Three whole condensation batches pay for the same volume of cold coolant: "+heat.tank(1).getFluidAmount()+" / "+coolant.getAmount()+" at "+FluidState.temperature(coolant));h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void liquidNitrogenLeavesAsTheRegisteredGasAmountAndCannotBeCreatedByAFan(GameTestHelper h){
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,3,6)),Direction.NORTH,2);var heat=be.thermal();
        heat.rowPort(0,0).fill(FluidState.with(new FluidStack(CEFluids.HYDROGEN_GAS.get(),10),12,FluidState.NORMAL_PRESSURE),EXECUTE);heat.rowPort(1,1).fill(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(),10),EXECUTE);for(int i=0;i<5;i++)be.tickProcess();
        int ratio=ThermalStreams.coolant(new FluidStack(CEProcessFluids.LIQUID_NITROGEN.get(),1)).outputAmount();
        h.assertTrue(heat.tank(1).getFluid().is(CEFluids.HYDROGEN_GAS.get()) && heat.tank(1).getFluidAmount()==10 && FluidState.temperature(heat.tank(1).getFluid())==2 && heat.tank(3).getFluid().is(CEFluids.NITROGEN_GAS.get()) && heat.tank(3).getFluidAmount()==10*ratio,"The common table preserves the nitrogen liquid/gas recipe ratio");
        for(var gas:new net.minecraft.world.level.material.Fluid[]{CEFluids.NITROGEN_GAS.get(),CEFluids.OXYGEN_GAS.get(),CEFusion.HELIUM_GAS.get()})h.assertTrue(ThermalStreams.cooling(new FluidStack(gas,1000))==null && ThermalStreams.liquefaction(new FluidStack(gas,1000))!=null,"Cryogenic products require the powered cold box");h.succeed();
    }
    @GameTest(template="test_fractionation")
    public static void aClosedAmmoniaCoolantLoopReturnsItsExactStartingLiquid(GameTestHelper h){
        var exchanger=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(6,3,6)),Direction.NORTH,2);var heat=exchanger.thermal();
        heat.rowPort(0,0).fill(FluidState.with(new FluidStack(CEFluids.HYDROGEN_GAS.get(),10),12,FluidState.NORMAL_PRESSURE),EXECUTE);heat.rowPort(1,1).fill(new FluidStack(CEProcessFluids.LIQUID_AMMONIA.get(),10),EXECUTE);for(int i=0;i<5;i++)exchanger.tickProcess();
        var vapour=heat.rowPort(1,0).drain(Integer.MAX_VALUE,EXECUTE);int ratio=ThermalStreams.coolant(new FluidStack(CEProcessFluids.LIQUID_AMMONIA.get(),1)).outputAmount();
        h.assertTrue(vapour.is(CEFluids.AMMONIA_GAS.get()) && vapour.getAmount()==10*ratio,"All ten liquid units leave as their finite gas equivalent");
        var fan=new BlockPos(18,5,6);var radiator=RadiatorTemperatureGameTests.cell(h,fan);((net.createplant.block.entity.CoolingFanBlockEntity)h.getBlockEntity(fan)).setSpeed(64);
        var inlet=((net.createplant.block.entity.RadiatorBlockEntity)h.getBlockEntity(fan.below(2).north())).port(Direction.NORTH);var outlet=((net.createplant.block.entity.RadiatorBlockEntity)h.getBlockEntity(fan.below(2).south())).port(Direction.SOUTH);inlet.fill(vapour,EXECUTE);for(int i=0;i<2000 && !inlet.getFluidInTank(0).isEmpty();i++)radiator.tick();
        // A fan cools only to ambient, where ammonia at normal pressure is still a gas.
        var returned=outlet.drain(Integer.MAX_VALUE,EXECUTE);
        h.assertTrue(returned.is(CEFluids.AMMONIA_GAS.get()) && FluidState.gas(returned) && FluidState.temperature(returned)<FluidState.temperature(vapour)
            && returned.getAmount()==vapour.getAmount() && inlet.getFluidInTank(0).isEmpty(),"The fan cools the vapour by one level without condensing it: "+returned.getAmount()+" at "+FluidState.temperature(returned));h.succeed();
    }
}
