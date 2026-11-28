package net.createplant.gametest;

import net.createplant.registry.CESharedChemicals;
import net.createplant.thermal.FluidForm;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEWaterTreatment;
import net.createplant.thermal.Compression;
import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IndustrialFertilizerGameTests {
    private static final BlockPos BASE = new BlockPos(4, 3, 4);

    @GameTest(template = "test_fractionation")
    public static void waterGasPowersMethanolAndAntifreezeProduction(GameTestHelper h) {
        var column = DistillationGameTests.build(h.getLevel(), h.absolutePos(BASE), 2, 2);
        var gasifierOutput = FluidThermal.at(new FluidStack(CEProcessFluids.WATER_GAS.get(), 2000), 473);
        h.assertTrue(DistillationGameTests.inlet(column, 1).fill(gasifierOutput, EXECUTE) == 2000,
            "The column accepts the gasifier's hot output");
        DistillationGameTests.run(column, 160);
        var co = drain(h, DistillationGameTests.outlet(column, 0), CEProcessFluids.CARBON_MONOXIDE.get(), 1000);
        var hydrogen = drain(h, DistillationGameTests.outlet(column, 1), CEFluids.HYDROGEN_GAS.get(), 1000);
        var compressed = Compression.convert(Compression.convert(hydrogen, true), true);
        h.assertTrue(FluidForm.COMPRESSED_HYDROGEN.matches(compressed), "Both existing compressor stages prepare the hydrogen");

        var tower = ReactionTowerGameTests.tower(h.getLevel(), h.absolutePos(BASE.east(8)), 3, CEItems.HYDROTREATING_CATALYST.toStack());
        var towerPort = ReactionTowerGameTests.port(tower);
        fill(h, towerPort, co.getFluid(), co.getAmount());
        fill(h, towerPort, compressed);
        tower.process(120);
        h.assertTrue(tower.status().equals("heat") && tower.tank(2).isEmpty(), "The catalytic tower does not synthesize cold methanol");
        ReactionTowerGameTests.heat(tower);
        tower.process(120);
        var methanol = drain(h, towerPort, CEProcessFluids.METHANOL.get(), 500);
        var basin = basin(h, HeatCondition.NONE);
        var input = basin.inputTank.getCapability();
        fill(h, input, methanol.getFluid(), methanol.getAmount());
        fill(h, input, Fluids.WATER, 500);
        mix(h, basin, "methanol_antifreeze");
        drain(h, basin.getTanks().getSecond().getCapability(), CEProcessFluids.ANTIFREEZE.get(), 1000);
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void carbonateLineRecoversAmmoniaAndSuppliesGlass(GameTestHelper h) {
        var vessel = vessel(h, HeatCondition.SUPERHEATED);
        var port = vessel.port(Direction.NORTH);
        insert(h, vessel.itemPort(), new ItemStack(Items.CALCITE, 2));
        run(vessel);
        var lime = take(h, vessel.itemPort(), CEItems.QUICKLIME.get(), 2);
        var co2 = drain(h, port, CEProcessFluids.CARBON_DIOXIDE.get(), 500);

        fill(h, port, CEChlorAlkali.BRINE.get(), 500);
        fill(h, port, CEFluids.AMMONIA_GAS.get(), 250);
        fill(h, port, co2.getFluid(), co2.getAmount());
        run(vessel);
        var bicarbonate = take(h, vessel.itemPort(), CEItems.SODIUM_BICARBONATE.get(), 4);
        var liquor = drain(h, port, CEProcessFluids.AMMONIUM_CHLORIDE_LIQUOR.get(), 500);

        insert(h, vessel.itemPort(), bicarbonate.copyWithCount(2));
        run(vessel);
        var soda = take(h, vessel.itemPort(), CEItems.SODIUM_CARBONATE.get(), 1);
        drain(h, port, CEProcessFluids.CARBON_DIOXIDE.get(), 250);

        insert(h, vessel.itemPort(), lime.copyWithCount(1));
        fill(h, port, liquor.getFluid(), liquor.getAmount());
        run(vessel);
        drain(h, port, CEFluids.AMMONIA_GAS.get(), 250);
        drain(h, port, CEWaterTreatment.NEUTRALIZED_WASTEWATER.get(), 250);

        var basin = basin(h, HeatCondition.SUPERHEATED);
        insert(h, basin.getInputInventory(), soda);
        insert(h, basin.getInputInventory(), lime.copyWithCount(1));
        insert(h, basin.getInputInventory(), new ItemStack(Items.RED_SAND, 2));
        mix(h, basin, "soda_lime_glass");
        take(h, basin.getOutputInventory(), Items.GLASS, 4);
        h.succeed();
    }

    @GameTest(template = "test_fractionation")
    public static void stirredVesselAndCreateBasinSupplyAllThreeNpkIngredients(GameTestHelper h) {
        var vessel = vessel(h, HeatCondition.HEATED);
        var port = vessel.port(Direction.NORTH);
        fill(h, port, net.createplant.thermal.FluidForm.LIQUID_AMMONIA, 100);
        fill(h, port, CEProcessFluids.CARBON_DIOXIDE.get(), 500);
        run(vessel);
        var urea = take(h, vessel.itemPort(), CEItems.UREA.get(), 2);
        drain(h, port, Fluids.WATER, 250);

        fill(h, port, CESharedChemicals.PHOSPHORIC_ACID.get(), 250);
        insert(h, vessel.itemPort(), new ItemStack(Items.CALCITE));
        run(vessel);
        var phosphate = take(h, vessel.itemPort(), CEItems.PHOSPHATE_FERTILIZER.get(), 2);

        var basin = basin(h, HeatCondition.HEATED);
        insert(h, basin.getInputInventory(), new ItemStack(Items.DRIED_KELP, 4));
        mix(h, basin, "potash_from_kelp");
        var potash = take(h, basin.getOutputInventory(), CEItems.POTASH.get(), 1);
        insert(h, basin.getInputInventory(), urea.copyWithCount(1));
        insert(h, basin.getInputInventory(), phosphate.copyWithCount(1));
        insert(h, basin.getInputInventory(), potash);
        mix(h, basin, "npk_fertilizer");
        take(h, basin.getOutputInventory(), CEItems.NPK_FERTILIZER.get(), 2);
        h.succeed();
    }

    private static ReactorMachineBlockEntity vessel(GameTestHelper h, HeatCondition heat) {
        var vessel = ReactorGameTests.assemble(h.getLevel(), h.absolutePos(BASE), ReactorModule.STIRRED, 1);
        h.assertTrue(vessel.formed(), "The stirring impeller forms an operational vessel");
        ReactorGameTests.heat(vessel, heat);
        return vessel;
    }

    private static void run(ReactorMachineBlockEntity vessel) {
        ReactorGameTests.speed(vessel, 32);
        for (int i = 0; i < 200; i++) vessel.tickProcess();
    }

    private static BasinBlockEntity basin(GameTestHelper h, HeatCondition heat) {
        var pos = BASE.east(14);
        h.setBlock(pos, AllBlocks.BASIN.get());
        if (heat != HeatCondition.NONE)
            h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL,
                heat == HeatCondition.SUPERHEATED ? BlazeBurnerBlock.HeatLevel.SEETHING : BlazeBurnerBlock.HeatLevel.KINDLED));
        var basin = (BasinBlockEntity) h.getBlockEntity(pos);
        basin.tick();
        return basin;
    }

    private static void mix(GameTestHelper h, BasinBlockEntity basin, String id) {
        var recipe = h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/" + id)).orElseThrow().value();
        h.assertTrue(BasinRecipe.apply(basin, recipe), "Create's basin performs " + id);
    }

    private static void fill(GameTestHelper h, IFluidHandler port, net.createplant.thermal.FluidForm form, int amount) {
        fill(h, port, form.stack(amount));
    }

    private static void fill(GameTestHelper h, IFluidHandler port, Fluid fluid, int amount) {
        h.assertTrue(port != null && port.fill(new FluidStack(fluid, amount), EXECUTE) == amount, "Native inlet accepts " + amount + " mB of " + fluid);
    }

    /** A stack that carries its temperature and pressure, such as compressed hydrogen. */
    private static void fill(GameTestHelper h, IFluidHandler port, FluidStack stack) {
        h.assertTrue(port != null && port.fill(stack, EXECUTE) == stack.getAmount(), "Native inlet accepts " + stack.getAmount() + " mB of " + stack.getFluid());
    }

    private static FluidStack drain(GameTestHelper h, IFluidHandler port, Fluid fluid, int amount) {
        h.assertTrue(port != null, "Native outlet exists for " + fluid);
        // As Create's pumps do: look at what the outlet holds, then drain exactly that fluid with its temperature and pressure.
        var request = new FluidStack(fluid, amount + 1);
        for (int tank = 0; tank < port.getTanks(); tank++) if (port.getFluidInTank(tank).is(fluid)) { request = port.getFluidInTank(tank).copyWithAmount(amount + 1); break; }
        var output = port.drain(request, EXECUTE);
        h.assertTrue(output.is(fluid) && output.getAmount() == amount, "Native outlet produced " + amount + " mB of " + fluid + ", found " + output);
        return output;
    }

    private static void insert(GameTestHelper h, IItemHandler port, ItemStack stack) {
        h.assertTrue(ItemHandlerHelper.insertItemStacked(port, stack, false).isEmpty(), "Native input takes " + stack);
    }

    private static ItemStack take(GameTestHelper h, IItemHandler port, Item item, int amount) {
        for (int slot = 0; slot < port.getSlots(); slot++) if (port.getStackInSlot(slot).is(item)) {
            var output = port.extractItem(slot, amount + 1, false);
            h.assertTrue(output.getCount() == amount, "Native outlet makes " + amount + " of " + item + ", found " + output);
            return output;
        }
        throw new AssertionError("Missing production output " + item);
    }
}
