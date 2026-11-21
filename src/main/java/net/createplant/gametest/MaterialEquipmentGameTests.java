package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import net.createplant.CreatePlant;
import net.createplant.registry.*;
import net.createplant.thermal.*;
import net.createplant.transport.*;
import net.createplant.transport.FluidHazards.Corrosion;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

/** Pipes and tanks of the six metals and stainless steel: which corrosion each resists, the vacuum-jacketed line and tank, and tanks that leak through. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class MaterialEquipmentGameTests {
    @GameTest(template = "test_reactor")
    public static void eachPipeMaterialHoldsItsOwnCorrosion(GameTestHelper h) {
        h.assertTrue(PipeMaterial.of(CETransport.STAINLESS_PIPE.get().defaultBlockState()) == PipeMaterial.STAINLESS
            && PipeMaterial.of(CETransport.STAINLESS_GLASS_PIPE.get().defaultBlockState()) == PipeMaterial.STAINLESS
            && PipeMaterial.of(CETransport.STAINLESS_ENCASED_PIPE.get().defaultBlockState()) == PipeMaterial.STAINLESS, "The whole stainless family is stainless steel");
        var acid = FluidHazards.named("sulfuric_acid");var liquor = FluidHazards.named("titanyl_sulfate_solution");var fluoride = FluidHazards.named("hydrogen_fluoride");
        h.assertTrue(acid.corrosion() == Corrosion.ACID && liquor.corrosion() == Corrosion.HOT_ACID && fluoride.corrosion() == Corrosion.FLUORIDE, "Fluids carry their corrosion class");
        h.assertTrue(PipeMaterial.COPPER.incompatibility(acid).equals("corrosion")
            && PipeMaterial.STAINLESS.incompatibility(acid).equals("safe"), "Copper rejects acid; stainless steel takes acid");
        h.assertTrue(PipeMaterial.of(CEPressure.REDUCER.get().defaultBlockState()) == PipeMaterial.VALVE && PipeMaterial.VALVE.incompatibility(acid).equals("safe"),
            "Pressure valves keep their acid-proof trim");
        h.assertTrue(PipeMaterial.STAINLESS.incompatibility(liquor).equals("corrosion") && PipeMaterial.ZIRCONIUM.incompatibility(liquor).equals("safe")
            && PipeMaterial.CERAMIC.incompatibility(liquor).equals("safe"), "Hot acid liquor needs zirconium, or stoneware at low pressure");
        h.assertTrue(!PipeMaterial.STAINLESS.resists(Corrosion.FLUORIDE) && !PipeMaterial.ZIRCONIUM.resists(Corrosion.FLUORIDE)
            && PipeMaterial.NICKEL_ALLOY.resists(Corrosion.FLUORIDE), "Corrosion ratings retain nickel alloy's fluoride resistance");
        var chlorine = FluidHazards.of(new net.neoforged.neoforge.fluids.FluidStack(net.createplant.registry.CEChlorAlkali.CHLORINE.get(), 1000));
        var caustic = FluidHazards.named("caustic_soda_solution");
        h.assertTrue(chlorine.gas() && chlorine.corrosion() == Corrosion.HALOGEN && PipeMaterial.STAINLESS.incompatibility(chlorine).equals("corrosion")
            && PipeMaterial.CERAMIC.incompatibility(chlorine).equals("safe") && PipeMaterial.NICKEL_ALLOY.incompatibility(chlorine).equals("safe"),
            "Chlorine gas pits stainless steel but passes stoneware and nickel alloy");
        h.assertTrue(caustic.corrosion() == Corrosion.BASE && PipeMaterial.CERAMIC.incompatibility(caustic).equals("corrosion")
            && PipeMaterial.STAINLESS.incompatibility(caustic).equals("safe"), "Caustic soda etches stoneware but not stainless steel");
        h.succeed();
    }
}
