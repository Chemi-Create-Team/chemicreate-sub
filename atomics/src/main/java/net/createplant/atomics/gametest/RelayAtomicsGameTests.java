package net.createplant.atomics.gametest;

import net.createplant.CreatePlant;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class RelayAtomicsGameTests {
    /** The relay bank adds and subtracts like integers, carries ripple one relay at a time, and the programs decide through it. */
    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void relayBankComputesTheControlLaw(GameTestHelper h) {
        var alu = new net.createplant.control.RelayAlu();
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
        h.assertTrue(net.createplant.atomics.relay.FissionRelayTarget.program(alu, rod, 90, 280, 8, 3000, 100, 0, true) == 1000, "Scram drives every rod in");
        h.assertTrue(net.createplant.atomics.relay.FissionRelayTarget.program(alu, rod, 90, 280, 8, 3000, 120, 40, false) > rod, "Over the setpoint the rods go in");
        h.assertTrue(net.createplant.atomics.relay.FissionRelayTarget.program(alu, rod, 90, 280, 8, 3000, 60, -40, false) < rod, "Under the setpoint with margin the rods come out");
        h.assertTrue(net.createplant.atomics.relay.FissionRelayTarget.program(alu, rod, 186, 280, 8, 3000, 60, -40, false) == rod + 80, "Hot fuel overrides the setpoint");
        h.succeed();
    }

}
