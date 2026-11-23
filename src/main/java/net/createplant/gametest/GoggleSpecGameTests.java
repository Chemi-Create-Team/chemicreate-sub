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

    private static void check(GameTestHelper helper, boolean ok, String what) {
        if (!ok) helper.fail("Goggle layout: " + what);
    }
}
