package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.thermal.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** Acceptance checks of docs/internal-temperature.md that need no machine: the heat balance of a tank and the burner heating. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class InternalTemperatureGameTests {
    private static FluidStack water(int amount,int level) { return FluidState.with(new FluidStack(Fluids.WATER,amount),level,FluidState.NORMAL_PRESSURE); }
    private static double energy(FluidStack stack) { return stack.isEmpty() ? 0 : HeatContent.capacity(stack)*FluidState.kelvin(FluidState.temperature(stack)); }

    @GameTest(template = "test_empty")
    public static void fillingAndDrainingNeverMakesOrLosesHeat(GameTestHelper h) {
        var tank=new ThermalFluidTank(8000,ignored->{});
        double in=0,out=0;
        for(int round=0;round<20;round++) {
            var hot=water(500,9);var cold=water(500,8);
            in+=energy(hot)+energy(cold);
            tank.fill(hot,EXECUTE);tank.fill(cold,EXECUTE);
            var drained=tank.drain(700,EXECUTE);
            out+=energy(drained);
            h.assertTrue(Math.abs(FluidState.temperature(tank.getFluid())-FluidState.temperatureLevel(tank.kelvin()))<=1,"The stack keeps the level of its real temperature");
        }
        double kept=HeatContent.capacity(tank.getFluid())*tank.kelvin()+tank.heat().residual();
        h.assertTrue(Math.abs(in-out-kept)<1,"Heat in equals heat out plus what the tank holds: "+(in-out-kept));
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void simulationAndSaveDoNotChangeTheTemperature(GameTestHelper h) {
        var tank=new ThermalFluidTank(8000,ignored->{});
        tank.fill(water(1000,9),EXECUTE);tank.fill(water(1000,8),EXECUTE);tank.drain(300,EXECUTE);
        double kelvin=tank.kelvin(),residual=tank.heat().residual();int amount=tank.getFluidAmount();
        tank.fill(water(500,9),SIMULATE);tank.drain(500,SIMULATE);
        h.assertTrue(tank.kelvin()==kelvin && tank.heat().residual()==residual && tank.getFluidAmount()==amount,"Simulating changes nothing");
        var saved=tank.writeToNBT(h.getLevel().registryAccess(),new CompoundTag());
        var restored=new ThermalFluidTank(8000,ignored->{});
        restored.readFromNBT(h.getLevel().registryAccess(),saved);
        h.assertTrue(restored.kelvin()==kelvin,"The real temperature survives a reload");
        var old=new CompoundTag();old.put("Fluid",saved.get("Fluid"));
        var fresh=new ThermalFluidTank(8000,ignored->{});fresh.readFromNBT(h.getLevel().registryAccess(),old);
        h.assertTrue(fresh.kelvin()==FluidState.kelvin(FluidState.temperature(fresh.getFluid())),"A tank saved without a temperature starts at its level's temperature");
        h.succeed();
    }

    private static int passesToWarm(int amount) {
        var fluids=java.util.List.of(water(amount,8));
        var heater=new TankHeating();
        int passes=0;
        while(FluidState.temperature(fluids.get(0))<=8 && passes<100) { heater.tick(fluids,1e5);passes++; }
        return passes;
    }

    @GameTest(template = "test_empty")
    public static void warmingTimeFollowsHeatCapacity(GameTestHelper h) {
        int one=passesToWarm(1000),two=passesToWarm(2000);
        h.assertTrue(one>0 && two==2*one,"Twice the water takes twice as long: "+one+" and "+two);
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aBoilingTankHoldsItsBoilingPointUntilTheLatentHeatIsIn(GameTestHelper h) {
        var tank=new ThermalFluidTank(8000,ignored->{});
        tank.fill(water(1000,8),EXECUTE);
        var fluids=java.util.List.of(tank.getFluid());
        var heater=new TankHeating();
        boolean held=false,boiled=false;
        for(int pass=0;pass<12 && !boiled;pass++) {
            heater.tick(fluids,TankHeating.LIT_WATTS,tank::capacityFor,java.util.List.of(tank.heat()));
            boiled=FluidState.gas(tank.getFluid());
            if(!boiled && Math.abs(tank.kelvin()-373.15)<.01)held=true;
        }
        h.assertTrue(held && boiled && tank.getFluidAmount()==1000,"The water waits at 100 C and then turns to steam");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void exchangerOutletsFollowTheHeatBalance(GameTestHelper h) {
        var hot=water(1000,9);var cold=water(1000,8);
        double hotK=FluidState.kelvin(9),coldK=FluidState.kelvin(8),capacity=HeatContent.capacity(hot),delta=hotK-coldK;
        double parallel=capacity/2*delta, counter=ThermalEquipment.COUNTERFLOW_EFFICIENCY*capacity*delta;
        var a=HeatContent.shift(hot,hotK,-parallel);var b=HeatContent.shift(cold,coldK,parallel);
        h.assertTrue(Math.abs(a.kelvin()-(hotK+coldK)/2)<.01 && Math.abs(b.kelvin()-(hotK+coldK)/2)<.01,"Parallel flow ends at the mean temperature");
        a=HeatContent.shift(hot,hotK,-counter);b=HeatContent.shift(cold,coldK,counter);
        h.assertTrue(Math.abs(a.kelvin()-(hotK-.9*delta))<.01 && Math.abs(b.kelvin()-(coldK+.9*delta))<.01 && b.kelvin()>a.kelvin(),"Counterflow moves 0.9 of the difference and crosses over");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aRadiatorPassCondensesSteam(GameTestHelper h) {
        var steam=water(1000,10);
        h.assertTrue(FluidState.gas(steam),"Level 10 water is steam");
        double kelvin=FluidState.kelvin(10);
        double needed=(kelvin-FluidState.kelvin(9))*HeatContent.capacity(steam)+HeatContent.latent(steam);
        var out=HeatContent.shift(steam,kelvin,-needed,FluidState.kelvin(FluidState.AMBIENT));
        h.assertTrue(!out.gas() && out.kelvin()<=373.16,"The heat of one level plus the latent heat turns steam to water");
        var partial=HeatContent.shift(steam,kelvin,-needed/2,FluidState.kelvin(FluidState.AMBIENT));
        h.assertTrue(partial.gas()==(partial.quality()>=.5) && partial.quality()>0 && partial.quality()<1,"Half the heat leaves the stream part way through condensing");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void drainingABoilingTankKeepsItsBoilingPointAndGasShare(GameTestHelper h) {
        var tank=new ThermalFluidTank(8000,ignored->{});
        tank.fill(water(1000,8),EXECUTE);
        var heater=new TankHeating();
        for(int pass=0;pass<2;pass++)heater.tick(java.util.List.of(tank.getFluid()),TankHeating.LIT_WATTS,tank::capacityFor,java.util.List.of(tank.heat()));
        double quality=tank.heat().quality(tank.getFluid());
        h.assertTrue(quality>0 && quality<1 && !FluidState.gas(tank.getFluid()),"Two passes leave the water part way through boiling");
        tank.drain(400,EXECUTE);
        h.assertTrue(tank.getFluidAmount()==600 && Math.abs(tank.kelvin()-373.15)<.01 && Math.abs(tank.heat().quality(tank.getFluid())-quality)<1e-9,"A part-way boiling tank keeps its temperature and gas share when some leaves");
        var other=new ThermalFluidTank(8000,ignored->{});
        int moved=tank.transferTo(other,300);
        h.assertTrue(moved==300 && Math.abs(other.kelvin()-373.15)<.01 && Math.abs(other.heat().quality(other.getFluid())-quality)<1e-9,"A transfer between tanks carries the temperature and gas share");
        h.succeed();
    }

    @GameTest(template = "test_empty")
    public static void aSolutionHeatedAloneBoilsItsWaterOffAndStopsAtDoubleStrength(GameTestHelper h) {
        var solution=new FluidStack(net.createplant.registry.CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(),1000);
        h.assertTrue(Concentration.applies(solution),"A solution in water boils its water off");
        var heater=new TankHeating();
        var fluids=java.util.List.of(solution);
        int steam=0;
        for(int pass=0;pass<400;pass++){heater.tick(fluids,TankHeating.SUPERHEATED_WATTS);steam+=heater.takeSteam();}
        h.assertTrue(steam>0 && solution.getAmount()==1000-steam,"Every mB of steam is a mB less solution");
        h.assertTrue(Concentration.permille(solution)>=1980 && solution.getAmount()<=505,"The solution ends at about twice its strength in half the volume");
        var room=new TankHeating();var again=new FluidStack(net.createplant.registry.CEMetals.COPPER_SULFATE_SOLUTION.get(),1000);
        room.steamRoom(0);for(int pass=0;pass<50;pass++)room.tick(java.util.List.of(again),TankHeating.SUPERHEATED_WATTS);
        h.assertTrue(again.getAmount()==1000,"With no room for steam nothing boils off");
        var solute=Concentration.permille(solution)*solution.getAmount();
        var mixed=FluidThermal.mix(solution,new FluidStack(solution.getFluid(),500),500);
        h.assertTrue(Math.abs(Concentration.permille(mixed)*mixed.getAmount()-(solute+500*1000))<=1500 && Concentration.permille(mixed)<Concentration.MAXIMUM,"Pouring plain solution into a strong one dilutes it and keeps the solute");
        h.succeed();
    }
}
