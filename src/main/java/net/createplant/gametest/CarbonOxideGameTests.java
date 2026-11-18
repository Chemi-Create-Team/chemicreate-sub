package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createplant.safety.FlammableGas;
import net.createplant.safety.ToxicGas;
import net.createplant.thermal.FluidMaterials;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public class CarbonOxideGameTests {
    @GameTest(template="test_reactor")
    public static void oxygenAndCoAreConsumedOnlyAfterACompleteCatalyticBatch(GameTestHelper h) {
        var tower=ReactionTowerGameTests.tower(h.getLevel(),h.absolutePos(new BlockPos(4,2,4)),3,CEItems.OXIDATION_CATALYST.toStack());
        var input=ReactionTowerGameTests.port(tower);var output=ReactionTowerGameTests.port(tower);
        var co=new FluidStack(CEProcessFluids.CARBON_MONOXIDE.get(),1000);var oxygen=new FluidStack(CEFluids.OXYGEN_GAS.get(),465);
        h.assertTrue(input.fill(co,SIMULATE)==1000 && input.getFluidInTank(0).isEmpty(),"A preview cannot create CO stock");
        h.assertTrue(input.fill(co,EXECUTE)==1000,"The bottom side accepts a finite CO feed");
        tower.process(220);
        h.assertTrue(output.drain(1000,SIMULATE).isEmpty(),"CO alone cannot produce CO2");
        h.assertTrue(input.fill(oxygen,EXECUTE)==465,"Oxygen must be supplied");
        tower.process(100);
        var request=new FluidStack(CEProcessFluids.CARBON_DIOXIDE.get(),2000);
        var preview=output.drain(request,SIMULATE);var product=output.drain(request,EXECUTE);
        h.assertTrue(preview.getAmount()==1000 && product.getAmount()==1000,"A paid 1000 CO + 465 O2 batch yields 1000 CO2 at the bottom of the reaction tower");
        tower.process(100);
        h.assertTrue(output.drain(request,EXECUTE).isEmpty(),"No free repeat batch");
        double mass=FluidMaterials.mass(co)+FluidMaterials.mass(oxygen);
        h.assertTrue(Math.abs(FluidMaterials.mass(product)-mass)<mass*.001,"Gas-mole stoichiometry conserves mass within reference-density rounding");
        h.assertTrue(FluidMaterials.gas(co.getFluid()) && FluidMaterials.gas(product.getFluid()),"Both streams use the gas transport path");
        h.assertTrue(FlammableGas.fuel(co) && !FlammableGas.fuel(product) && ToxicGas.strength(co)>=0,"CO is toxic and flammable, CO2 is nonflammable");
        h.assertTrue(Math.abs(FluidThermal.properties(co).joulesPerKilogramKelvin()-1040)<1,"CO carries its registered heat capacity");
        h.succeed();
    }
}
