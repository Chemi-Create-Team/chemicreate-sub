package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEOstwaldFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

/** The Ostwald absorption step belongs to the absorption tower, never to the stirred reactor. */
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NitricAcidGameTests {
    private static final BlockPos BASE = new BlockPos(21, 2, 4);

    private static ReactorMachineBlockEntity build(GameTestHelper helper, int storeys) {
        return ReactorGameTests.assemble(helper.getLevel(), helper.absolutePos(BASE), ReactorModule.TOWER, storeys);
    }

    private static void run(ReactorMachineBlockEntity vessel, int ticks) { for (int i = 0; i < ticks; i++) vessel.tickProcess(); }

    private static FluidStack product(IFluidHandler port, int index) { return port.getFluidInTank(index + 2); }

    @GameTest(template = "test_empty")
    public static void nitricAcidIsMadeOnlyInTheAbsorptionTower(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CreatePlant.location("pressure_reaction/nitric_acid")).isEmpty(),
            "The old stirred-reactor nitric acid recipe is gone");
        var recipe = ProcessRecipe.all().stream().filter(r -> r.id().equals("absorb_nitric_acid")).findFirst().orElseThrow();
        helper.assertTrue(recipe.module() == ProcessModule.ABSORPTION_TRAYS && recipe.ticks() == 100, "Absorption is one 100-tick batch a storey");
        helper.assertTrue(recipe.inputs().get(0).is(CEOstwaldFluids.NITROGEN_DIOXIDE.get()) && recipe.inputs().get(0).getAmount() == 300
            && recipe.inputs().get(1).is(Fluids.WATER) && recipe.inputs().get(1).getAmount() == 100,
            "The feeds are 300 mB nitrogen dioxide and 100 mB water");
        helper.assertTrue(recipe.outputs().get(0).is(CEFluids.NITRIC_ACID.get()) && recipe.outputs().get(0).getAmount() == 200
            && recipe.outputs().get(1).is(CEOstwaldFluids.NITRIC_OXIDE.get()) && recipe.outputs().get(1).getAmount() == 100,
            "The products are 200 mB nitric acid and 100 mB recyclable nitric oxide");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void eachStoreyAbsorbsOneBatchWithoutRotation(GameTestHelper helper) {
        var vessel = build(helper, 4);
        helper.assertTrue(vessel.formed() && vessel.module() == ReactorModule.TOWER && vessel.sections() == 4, "Four storeys form the absorption tower: " + vessel.status());
        helper.assertTrue(vessel.calculateStressApplied() == 0, "The tower takes no rotation");
        var port = vessel.port(Direction.NORTH);
        helper.assertTrue(port.fill(new FluidStack(CEFluids.NITRIC_ACID.get(), 200), SIMULATE) == 0, "A product is no feed");
        helper.assertTrue(port.fill(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 1500), EXECUTE) == 1500
            && port.fill(new FluidStack(Fluids.WATER, 500), EXECUTE) == 500, "Five batches of both feeds enter");
        run(vessel, 99);
        helper.assertTrue(vessel.status().equals("running") && product(port, 0).isEmpty(), "A batch takes its full 100 ticks: " + vessel.status());
        run(vessel, 2);
        helper.assertTrue(product(port, 0).is(CEFluids.NITRIC_ACID.get()) && product(port, 0).getAmount() == 800
            && product(port, 1).is(CEOstwaldFluids.NITRIC_OXIDE.get()) && product(port, 1).getAmount() == 400,
            "Four storeys make four batches at once: 800 mB nitric acid and 400 mB nitric oxide");
        helper.assertTrue(port.getFluidInTank(0).getAmount() == 300 && port.getFluidInTank(1).getAmount() == 100, "The fifth batch waits for the next cycle");
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void ordinaryWaterMakesNitricAcid(GameTestHelper helper) {
        var vessel = build(helper, 4);
        var port = vessel.port(Direction.NORTH);
        helper.assertTrue(port.fill(new FluidStack(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), 300), EXECUTE) == 300
            && port.fill(new FluidStack(Fluids.WATER, 100), EXECUTE) == 100, "Ordinary water is accepted");
        run(vessel, 101);
        helper.assertTrue(product(port, 0).is(CEFluids.NITRIC_ACID.get()) && product(port, 0).getAmount() == 200
            && product(port, 1).is(CEOstwaldFluids.NITRIC_OXIDE.get()) && product(port, 1).getAmount() == 100,
            "Ordinary water gives 200 mB nitric acid and recyclable nitric oxide: " + product(port, 0));
        helper.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void threeStoreysAreNoTower(GameTestHelper helper) {
        var vessel = build(helper, 3);
        helper.assertTrue(!vessel.formed() && vessel.port(Direction.NORTH) == null, "Three storeys do not form the tower: " + vessel.status());
        helper.succeed();
    }
}
