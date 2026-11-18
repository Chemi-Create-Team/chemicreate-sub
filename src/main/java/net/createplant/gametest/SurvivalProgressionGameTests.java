package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.LoggerFactory;

@ExplicitGameTest
@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurvivalProgressionGameTests {
    private SurvivalProgressionGameTests() { }

    @GameTest(template = "test_fusion", timeoutTicks = 80000)
    public static void waterwheelWorkshopMakesSteelThenStainlessSheets(GameTestHelper h) {
        var workshop = new SurvivalMetalsWorkshop(h.getLevel(), h.absolutePos(BlockPos.ZERO));
        h.onEachTick(() -> {
            try {
                if (!workshop.tick()) return;
            } catch (RuntimeException error) {
                LoggerFactory.getLogger(SurvivalProgressionGameTests.class).info("SURVIVAL stopped after {}", workshop.measurements());
                throw error;
            }
            LoggerFactory.getLogger(SurvivalProgressionGameTests.class).info("SURVIVAL progression {}", workshop.measurements());
            h.succeed();
        });
    }
}
