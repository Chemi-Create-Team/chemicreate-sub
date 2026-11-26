package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.relay.CERelayComputer;
import net.createplant.relay.RelayComputerBlockEntity;
import net.createplant.relay.RelayRackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public final class RelayComputerGameTests {
    @GameTest(template = "test_reactor", timeoutTicks = 80)
    public static void relayComputerFormsWithTwoRacksAndYieldsToRedstone(GameTestHelper h) {
        BlockPos base = new BlockPos(3, 2, 3);
        h.setBlock(base, CERelayComputer.COMPUTER.get());
        h.setBlock(base.above(), CERelayComputer.RACK.get());
        h.runAfterDelay(12, () -> {
            var computer = (RelayComputerBlockEntity) h.getBlockEntity(base);
            h.assertTrue(computer.status().equals("unformed"), "One rack is not enough: " + computer.status());
            h.setBlock(base.above(2), CERelayComputer.RACK.get());
        });
        h.runAfterDelay(24, () -> {
            var computer = (RelayComputerBlockEntity) h.getBlockEntity(base);
            h.assertTrue(computer.status().equals("no_target"), "Formed, but nothing to control here: " + computer.status());
            h.setBlock(base.north(), Blocks.REDSTONE_BLOCK);
        });
        h.runAfterDelay(36, () -> {
            var computer = (RelayComputerBlockEntity) h.getBlockEntity(base);
            h.assertTrue(computer.status().equals("manual") && !h.getBlockState(base.above()).getValue(RelayRackBlock.ACTIVE), "Redstone hands control back: " + computer.status());
            h.succeed();
        });
    }
}
