package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.thermal.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class ThermalStabilityGameTests {
    @GameTest(template="test_fractionation")
    public static void intermittentUnequalStreamsConserveHeat(GameTestHelper h) {
        var be=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(5,2,5)),Direction.NORTH,1);int incoming=0,outgoing=0;
        for(int tick=1;tick<=240;tick++){
            incoming+=be.thermal().tank(2).fill(new FluidStack(net.createplant.registry.CEProcessFluids.HOT_OLEFINS.get(),1),EXECUTE);
            if(tick%20==1)incoming+=be.thermal().tank(0).fill(new FluidStack(Fluids.WATER,1),EXECUTE);
            be.tickProcess();
            for(int slot:new int[]{1,3}){var out=be.thermal().tank(slot).drain(8000,EXECUTE);outgoing+=out.getAmount();h.assertTrue(out.isEmpty() || out.is(slot==1 ? net.createplant.registry.CEProcessFluids.WARM_WATER.get() : net.createplant.registry.CEProcessFluids.COOLED_OLEFINS.get()),"Pulsed conversions keep the correct row products");}
        }
        int remaining=be.thermal().tank(0).getFluidAmount()+be.thermal().tank(2).getFluidAmount();
        h.assertTrue(outgoing==24 && incoming==outgoing+remaining,"Twelve finite pulsed pairs convert without consuming unmatched feed");h.succeed();
    }
}
