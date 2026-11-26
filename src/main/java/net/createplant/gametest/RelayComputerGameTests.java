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
    /** The relay bank adds and subtracts like integers, carries ripple one relay at a time, and the programs decide through it. */
    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void relayBankComputesTheControlLaw(GameTestHelper h) {
        var alu = new net.createplant.relay.RelayAlu();
        var rnd = new java.util.Random(7);
        for (int k = 0; k < 2000; k++) {
            int x = rnd.nextInt(2000) - 1000, y = rnd.nextInt(2000) - 1000;
            h.assertTrue(alu.add(x, y) == x + y && alu.sub(x, y) == x - y && alu.ge(x, y) == (x >= y), x + " ? " + y);
        }
        alu.takeSteps(); alu.takeFlips();
        alu.add(0, 0); alu.takeSteps();
        alu.add(2047 >> 1, 1);
        long steps = alu.takeSteps();
        h.assertTrue(steps >= 10, "A long carry ripples through the bank relay by relay: " + steps + " steps");
        h.assertTrue(alu.takeFlips() > 10, "Relays switch and so click");
        int rod = 500;
        h.assertTrue(net.createplant.relay.RelayPrograms.fission(alu, rod, 90, 280, 8, 3000, 100, 0, true) == 1000, "Scram drives every rod in");
        h.assertTrue(net.createplant.relay.RelayPrograms.fission(alu, rod, 90, 280, 8, 3000, 120, 40, false) > rod, "Over the setpoint the rods go in");
        h.assertTrue(net.createplant.relay.RelayPrograms.fission(alu, rod, 90, 280, 8, 3000, 60, -40, false) < rod, "Under the setpoint with margin the rods come out");
        h.assertTrue(net.createplant.relay.RelayPrograms.fission(alu, rod, 186, 280, 8, 3000, 60, -40, false) == rod + 80, "Hot fuel overrides the setpoint");
        h.succeed();
    }

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
