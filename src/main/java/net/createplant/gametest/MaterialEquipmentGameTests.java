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
        h.assertTrue(PipeMaterial.REINFORCED.incompatibility(acid).equals("corrosion")
            && PipeMaterial.STAINLESS.incompatibility(acid).equals("safe"), "Reinforced steel rejects acid; stainless steel takes acid");
        h.assertTrue(PipeMaterial.of(CEPressure.REDUCER.get().defaultBlockState()) == PipeMaterial.VALVE && PipeMaterial.VALVE.incompatibility(acid).equals("safe"),
            "Pressure valves keep their acid-proof trim");
        h.assertTrue(PipeMaterial.STAINLESS.incompatibility(liquor).equals("corrosion") && PipeMaterial.ZIRCONIUM.incompatibility(liquor).equals("safe")
            && PipeMaterial.RESIN.incompatibility(liquor).equals("safe"), "Hot acid liquor needs zirconium, or resin at low pressure");
        h.assertTrue(PipeMaterial.STAINLESS.incompatibility(fluoride).equals("corrosion") && PipeMaterial.ZIRCONIUM.incompatibility(fluoride).equals("corrosion")
            && PipeMaterial.NICKEL_ALLOY.incompatibility(fluoride).equals("safe"), "Only the nickel alloy holds fluorides");
        h.succeed();
    }
}
