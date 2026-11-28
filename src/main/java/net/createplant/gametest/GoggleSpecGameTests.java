package net.createplant.gametest;

import net.createplant.registry.CESharedChemicals;
import net.createplant.thermal.FluidForm;
import java.util.ArrayList;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.client.GoggleSpec;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEMetals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;

/** The goggle layout's number rules: buckets from 1,000 mB, whole buckets from 10,000 mB, mB under Shift, and "&lt;0.1%" for a trace. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class GoggleSpecGameTests {
    @GameTest(template = "test_empty")
    public static void amountsAndSharesFollowTheSpec(GameTestHelper helper) {
        check(helper, GoggleSpec.amount(500, false).equals("500 mB"), "under a bucket stays in mB");
        check(helper, GoggleSpec.amount(2500, false).equals("2.5 B"), "one decimal from 1,000 mB");
        check(helper, GoggleSpec.amount(8000, false).equals("8 B"), "a whole bucket drops .0");
        check(helper, GoggleSpec.amount(2_304_000, false).equals("2,304 B"), "from 10,000 mB whole buckets with commas");
        check(helper, GoggleSpec.amount(8000, true).equals("8,000 mB"), "Shift gives mB");
        check(helper, GoggleSpec.percent(991, 1000).equals("99.1%"), "one decimal");
        check(helper, GoggleSpec.percent(1, 10_000).equals("<0.1%"), "a trace is not 0.0%");
        helper.succeed();
    }

    /** A batch of 250 mB in and 100 mB out, converted in uneven steps, takes and gives exactly its amounts and never makes a product before its feed is paid. */
    @GameTest(template = "test_empty")
    public static void aReactionConvertsAWholeBatchInSmallSteps(GameTestHelper helper) {
        double flow = 0;
        int in = 0, out = 0;
        for (double step : new double[] {0.013, 0.2, 0.0007, 0.35, 0.1, 0.2, 0.1, 0.0363}) {
            int took = net.createplant.chemistry.ReactionFlow.owed(250, flow, step), gave = net.createplant.chemistry.ReactionFlow.made(100, flow, step);
            in += took;
            out += gave;
            check(helper, out * 250 <= in * 100 + 250, "no product before its feed");
            flow = net.createplant.chemistry.ReactionFlow.next(flow, step);
        }
        check(helper, (flow < 1e-9 || flow > 1 - 1e-9) && in == 250 && out == 100, "a whole batch takes 250 and gives 100 (took " + in + ", gave " + out + ", at " + flow + ")");
        helper.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void stirredStockListsAllFluidsAndKeepsWaterAndSteamSeparate(GameTestHelper helper) {
        var level = helper.getLevel();
        var vessel = (LiddedVesselBlockEntity) ReactorGameTests.assemble(level, helper.absolutePos(new BlockPos(4, 2, 4)), ReactorModule.STIRRED, 2);
        var saved = vessel.saveWithoutMetadata(level.registryAccess());
        var fluids = new FluidStack[] {new FluidStack(Fluids.WATER, 101), FluidForm.STEAM.stack(102),
            new FluidStack(CEFluids.OXYGEN_GAS.get(), 103), new FluidStack(CEFluids.NITROGEN_GAS.get(), 104),
            new FluidStack(CEMetals.ZIRCONIUM_SULFATE_SOLUTION.get(), 105), new FluidStack(CESharedChemicals.SULFURIC_ACID.get(), 106),
            new FluidStack(CEFluids.HYDROGEN_GAS.get(), 107)};
        for (int i = 0; i < fluids.length; i++) saved.put("Tank" + i, fluids[i].save(level.registryAccess()));
        vessel.loadWithComponents(saved, level.registryAccess());
        var tooltip = new ArrayList<Component>();
        GoggleSpec.vessel(vessel, tooltip, false);
        var text = tooltip.stream().map(Component::getString).toList();
        for (var fluid : fluids) check(helper, text.stream().anyMatch(line -> line.contains(" " + fluid.getAmount() + " mB")),
            "Every stock has a row in mB without Shift: " + text);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail("Goggle layout: " + what);
    }
}
