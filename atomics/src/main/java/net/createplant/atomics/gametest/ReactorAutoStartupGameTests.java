package net.createplant.atomics.gametest;

import net.createplant.gametest.ExplicitGameTest;
import net.createplant.CreatePlant;
import net.createplant.atomics.nuclear.ReactorFuel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
@ExplicitGameTest
public class ReactorAutoStartupGameTests {
    @GameTest(template="test_rbmk",timeoutTicks=11000)
    public static void circularFiveEnrichedColdStart(GameTestHelper h) { CompactFuelLongGameTests.start(h,5,3,true,ReactorFuel.COMPACT_ENRICHED,true); }
    @GameTest(template="test_rbmk",timeoutTicks=11000)
    public static void squareFiveEnrichedColdStart(GameTestHelper h) { CompactFuelLongGameTests.start(h,5,3,false,ReactorFuel.COMPACT_ENRICHED,true); }
    @GameTest(template="test_rbmk",timeoutTicks=11000)
    public static void circularFiveOrdinaryColdStart(GameTestHelper h) { CompactFuelLongGameTests.start(h,5,3,true,ReactorFuel.URANIUM,true); }
    @GameTest(template="test_rbmk",timeoutTicks=11000)
    public static void standardMaximumColdStart(GameTestHelper h) { CompactFuelLongGameTests.start(h,13,7,true,ReactorFuel.URANIUM,true); }
}
