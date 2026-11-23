package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.client.GoggleSpec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail("Goggle layout: " + what);
    }
}
