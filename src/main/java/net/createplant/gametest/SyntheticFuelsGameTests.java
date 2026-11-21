package net.createplant.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEPetroFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEWaterTreatment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntheticFuelsGameTests {
    private static final BlockPos CO_COMPRESSOR = new BlockPos(6, 3, 12);
    private static final BlockPos H2_COMPRESSOR = new BlockPos(11, 3, 12);

    @GameTest(template = "test_fractionation", timeoutTicks = 150)
    public static void gasificationThroughShiftAndFtSuppliesAllCutsAndResin(GameTestHelper h) {
        var raw = GasifierGameTests.makeWaterGas(h, 2);
        h.assertTrue(raw.getAmount()==1200 && net.createplant.thermal.SteamCondensate.vapour(raw), "Two gasifier batches deliver wet feed");
        var exchanger=ThermalControlGameTests.build(h.getLevel(),h.absolutePos(new BlockPos(11,8,5)),Direction.EAST,3);
        fill(h,ThermalControlGameTests.tube(exchanger,0),raw);
        fill(h,ThermalControlGameTests.shell(exchanger,1),stack(Fluids.WATER,1200));
        for(int i=0;i<25;i++)exchanger.tickProcess();
        var outlet=ThermalControlGameTests.tube(exchanger,1);
        var water=outlet.drain(1200,EXECUTE);
        h.assertTrue(water.is(Fluids.WATER) && water.getAmount()==200,"Steam leaves the exchanger as water");
        var cooled=outlet.drain(1200,EXECUTE);
        h.assertTrue(cooled.is(CEProcessFluids.WATER_GAS.get()) && cooled.getAmount()==1000,"The gas leaves in a separate phase");
        var washAt = new BlockPos(11, 2, 5);
        AbsorptionTowerGameTests.tower(h.getLevel(), h.absolutePos(washAt), 4, true);
        var washBottom = port(h, washAt.west(), Direction.WEST);
        var washTop = port(h, washAt.above(3).west(), Direction.WEST);
        h.assertTrue(washBottom.fill(raw,SIMULATE)==0,"Uncooled wet gas cannot bypass cooling");
        fill(h, washBottom, water);
        fill(h, washBottom, cooled);
        var clean = drain(h, washTop, CEProcessFluids.PURIFIED_SYNGAS.get(), 1000);
        h.assertTrue(washBottom.drain(1000,EXECUTE).getAmount()==200,"The bottom outlet gives the recovered water");
        h.assertTrue(washBottom.drain(clean, SIMULATE).isEmpty(), "Clean gas leaves only from the upper port");

        var split = cuts(h, new BlockPos(15, 2, 4), clean, "separate_purified_syngas");
        var hydrogen = split.getFirst();
        var monoxide = split.getLast();
        h.assertTrue(hydrogen.is(CEFluids.HYDROGEN_GAS.get()) && monoxide.is(CEProcessFluids.CARBON_MONOXIDE.get()),
            "Light hydrogen leaves from above the carbon monoxide outlet");

        var shift = tower(h, new BlockPos(20, 3, 4), CEItems.SHIFT_CATALYST_CORE.toStack());
        var shiftPort = ReactionTowerGameTests.port(shift);
        fill(h, shiftPort, monoxide.copyWithAmount(250));
        fill(h, shiftPort, stack(Fluids.WATER, 250));
        shift.process(90);
        h.assertTrue(shift.status().equals("heat") && shift.tank(0).getAmount() == 250, "Cold shift keeps the full input");
        ReactionTowerGameTests.heat(shift);
        shift.process(90);
        drain(h, shiftPort, CEProcessFluids.CARBON_DIOXIDE.get(), 250);
        var shiftedHydrogen = drain(h, shiftPort, CEFluids.HYDROGEN_GAS.get(), 250);
        h.assertTrue(shift.layer(0).catalystStack().getDamageValue() == 1, "A completed cycle wears one shift core");

        ReciprocatingCompressorGameTests.poweredCompressor(h, CO_COMPRESSOR);
        ReciprocatingCompressorGameTests.poweredCompressor(h, H2_COMPRESSOR);
        fill(h, port(h, CO_COMPRESSOR, Direction.UP), monoxide.copyWithAmount(250));
        fill(h, port(h, H2_COMPRESSOR, Direction.UP), hydrogen);
        fill(h, port(h, H2_COMPRESSOR, Direction.UP), shiftedHydrogen);
        h.runAfterDelay(80, () -> {
            var compressedCo = drain(h, port(h, CO_COMPRESSOR, Direction.DOWN), CEProcessFluids.PRESSURISED_CARBON_MONOXIDE.get(), 250);
            var compressedH2 = drain(h, port(h, H2_COMPRESSOR, Direction.DOWN), CEProcessFluids.PRESSURISED_HYDROGEN.get(), 750);

            var ft = tower(h, new BlockPos(20, 3, 11), CEItems.COBALT_FT_CATALYST_CORE.toStack());
            var ftPort = ReactionTowerGameTests.port(ft);
            h.assertTrue(ftPort.fill(monoxide, SIMULATE) == 0 && ftPort.fill(hydrogen, SIMULATE) == 0,
                "Atmospheric CO and H2 do not enter a cobalt FT charge");
            fill(h, ftPort, compressedCo);
            fill(h, ftPort, compressedH2.copyWithAmount(500));
            ReactionTowerGameTests.heat(ft);
            ft.process(110);
            h.assertTrue(ft.status().equals("no_feed") && ft.tank(0).getAmount() == 250, "The iron ratio cannot start cobalt FT");
            fill(h, ftPort, compressedH2.copyWithAmount(250));
            ft.process(110);
            var crude = drain(h, ftPort, CEProcessFluids.SYNTHETIC_CRUDE_OIL.get(), 750);
            var offgas = drain(h, ftPort, CEProcessFluids.FT_OFFGAS.get(), 125);

            var products = cuts(h, new BlockPos(24, 3, 5), crude.copyWithAmount(500), "distill_synthetic_crude");
            h.assertTrue(products.get(0).is(CEProcessFluids.FT_OFFGAS.get()) && products.get(1).is(CEProcessFluids.SYNTHETIC_NAPHTHA.get())
                && products.get(2).is(CEProcessFluids.LOW_SULFUR_DIESEL.get()) && products.get(3).is(CEProcessFluids.MOLTEN_PARAFFIN.get()),
                "Four native column ports expose light gas, naphtha, diesel and wax in order");
            var olefins = cuts(h, new BlockPos(27, 3, 5), offgas.copyWithAmount(offgas.getAmount() + products.getFirst().getAmount()), "separate_ft_offgas");
            h.assertTrue(olefins.getFirst().is(CEPetroFluids.ETHYLENE.get()) && olefins.getFirst().getAmount() == 150,
                "The two off-gas sources reach the existing ethylene stream");

            var reformAt = new BlockPos(26, 3, 11);
            var reformer = tower(h, reformAt, CEItems.ZSM5_CATALYST_CORE.toStack());
            var reform = ReactionTowerGameTests.port(reformer);
            ReactionTowerGameTests.heat(reformer);
            fill(h, reform, products.get(1));
            reformer.process(130);
            h.assertTrue(reformer.status().equals("heat"), "Naphtha reforming requires superheat");
            burner(h, reformAt, BlazeBurnerBlock.HeatLevel.SEETHING);
            reformer.process(130);
            var aromatics = drain(h, reform, CEProcessFluids.BTX.get(), 100);
            drain(h, reform, CEProcessFluids.FT_OFFGAS.get(), 50);

            var resinAt = new BlockPos(19, 3, 17);
            var vessel = ReactorGameTests.assemble(h.getLevel(), h.absolutePos(resinAt), ReactorModule.STIRRED, 1);
            ReactorGameTests.heat(vessel, HeatCondition.HEATED);
            ReactorGameTests.speed(vessel, 32);
            var resinIn = vessel.port(Direction.NORTH);
            fill(h, resinIn, aromatics);
            fill(h, resinIn, olefins.getFirst());
            for (int i = 0; i < 110; i++) vessel.tickProcess();
            var items = vessel.itemPort();
            var resin = ItemStack.EMPTY;
            for (int i = 0; i < items.getSlots(); i++)
                if (items.getStackInSlot(i).is(CEItems.INDUSTRIAL_RESIN.get())) resin = items.extractItem(i, 64, false);
            h.assertTrue(resin.is(CEItems.INDUSTRIAL_RESIN.get()) && resin.getCount() == 2,
                "Sealed reactor charges from the full line make the existing industrial resin");

            var basinAt = new BlockPos(25, 3, 18);
            h.setBlock(basinAt, AllBlocks.BASIN.get());
            var basin = (BasinBlockEntity) h.getBlockEntity(basinAt);
            fill(h, basin.inputTank.getCapability(), products.get(3));
            fill(h, basin.inputTank.getCapability(), stack(Fluids.WATER, 100));
            var wax = h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/paraffin_wax")).orElseThrow().value();
            h.assertTrue(BasinRecipe.apply(basin, wax) && basin.getOutputInventory().getStackInSlot(0).is(CEItems.PARAFFIN_WAX.get()),
                "The heavy cut makes usable paraffin wax in the original Create basin");
            h.succeed();
        });
    }

    @GameTest(template = "test_fractionation")
    public static void ironFtAndMethanolRunInTheSameTowerArchitecture(GameTestHelper h) {
        var ft = tower(h, new BlockPos(5, 3, 5), CEItems.IRON_FT_CATALYST_CORE.toStack());
        ReactionTowerGameTests.heat(ft);
        var ftPort = ReactionTowerGameTests.port(ft);
        fill(h, ftPort, stack(CEProcessFluids.PRESSURISED_CARBON_MONOXIDE.get(), 250));
        fill(h, ftPort, stack(CEProcessFluids.PRESSURISED_HYDROGEN.get(), 500));
        ft.process(139);
        h.assertTrue(ft.tank(2).isEmpty() && ft.tank(0).getAmount() == 250, "Iron FT settles feeds and products only on the last tick");
        ft.process(1);
        drain(h, ftPort, CEProcessFluids.SYNTHETIC_CRUDE_OIL.get(), 500);
        drain(h, ftPort, CEProcessFluids.FT_OFFGAS.get(), 125);

        var mtg = tower(h, new BlockPos(15, 3, 5), CEItems.ZSM5_CATALYST_CORE.toStack());
        ReactionTowerGameTests.heat(mtg);
        var mtgPort = ReactionTowerGameTests.port(mtg);
        fill(h, mtgPort, stack(CEProcessFluids.METHANOL.get(), 250));
        mtg.process(120);
        drain(h, mtgPort, CEProcessFluids.BTX.get(), 100);
        drain(h, mtgPort, Fluids.WATER, 150);
        h.succeed();
    }

    private static java.util.List<FluidStack> cuts(GameTestHelper h, BlockPos pos, FluidStack feed, String id) {
        var fraction = DistillationProfile.fraction(id);
        int height = DistillationProfile.requiredHeight(id);
        var column = DistillationGameTests.build(h.getLevel(), h.absolutePos(pos), 2, height);
        fill(h, DistillationGameTests.inlet(column, DistillationProfile.feedLayer(height)), feed);
        DistillationGameTests.run(column, fraction.ticks() + 10);
        var products = new java.util.ArrayList<FluidStack>();
        for (int i = 0; i < fraction.outputs().size(); i++) {
            var cut = fraction.outputs().get(i);
            int amount = cut.getAmount() * (feed.getAmount() / fraction.input().getAmount());
            products.add(drain(h, DistillationGameTests.outlet(column, DistillationProfile.fractionLayer(i + 1, height,
                fraction.outputs().size())), cut.getFluid(), amount));
        }
        return products;
    }

    private static ReactionTowerBlockEntity tower(GameTestHelper h, BlockPos pos, ItemStack core) {
        var tower = ReactionTowerGameTests.tower(h.getLevel(), h.absolutePos(pos), 3, ItemStack.EMPTY);
        tower.insert(core);
        return tower;
    }

    private static void burner(GameTestHelper h, BlockPos corner, BlazeBurnerBlock.HeatLevel heat) {
        h.setBlock(corner.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat));
    }

    private static IFluidHandler port(GameTestHelper h, BlockPos pos, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
    }

    private static FluidStack stack(Fluid fluid, int amount) {
        return new FluidStack(fluid, amount);
    }

    private static void fill(GameTestHelper h, IFluidHandler port, FluidStack fluid) {
        h.assertTrue(port != null && port.fill(fluid, EXECUTE) == fluid.getAmount(), "Failed to insert " + fluid);
    }

    private static FluidStack drain(GameTestHelper h, IFluidHandler port, Fluid fluid, int amount) {
        h.assertTrue(port != null, "No port for " + fluid);
        var output = port.drain(amount + 1, SIMULATE);
        if (!output.is(fluid)) output = port.drain(stack(fluid, amount + 1), SIMULATE);
        h.assertTrue(output.is(fluid) && output.getAmount() == amount, "Expected " + amount + " of " + fluid + ", got " + output);
        return port.drain(output, EXECUTE);
    }
}
