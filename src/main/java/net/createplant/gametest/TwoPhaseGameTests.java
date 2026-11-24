package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.thermal.FluidState;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.thermal.TwoPhase;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class TwoPhaseGameTests {
    @GameTest(template="test_reactor")
    public static void aPartlyBoiledStreamLeavesAsGasAndLiquidAndSplits(GameTestHelper h) {
        var tank=new ThermalFluidTank(16000,stack->{ });
        tank.fillAt(FluidState.with(new FluidStack(Fluids.WATER,1000),9,4),373.15,.5,IFluidHandler.FluidAction.EXECUTE);
        var out=tank.drain(400,IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(out.getAmount()==400 && TwoPhase.is(out) && TwoPhase.share(out)==.5,"A half-boiled content leaves with half its share as gas: "+out);
        var split=TwoPhase.split(out,400);
        h.assertTrue(split.gas().getAmount()+split.liquid().getAmount()==400 && FluidState.gas(split.gas()) && !FluidState.gas(split.liquid())
            && split.gas().getAmount()==200,"The separator gets 200 mB of gas and 200 mB of liquid, none made or lost: "+split);
        h.succeed();
    }
}
