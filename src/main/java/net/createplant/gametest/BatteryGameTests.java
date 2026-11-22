package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.BatteryChemistry;
import net.createplant.block.BatteryStructure;
import net.createplant.block.entity.BatteryTerminalBlockEntity;
import net.createplant.registry.CEBattery;
import net.createplant.registry.CESafety;
import net.createplant.registry.CEUranium;
import net.createplant.safety.CorrosiveContact;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class BatteryGameTests {
    private static void box(GameTestHelper h, BlockPos corner, int dx, int dy, int dz) {
        for (int x = 0; x < dx; x++) for (int y = 0; y < dy; y++) for (int z = 0; z < dz; z++)
            h.setBlock(corner.offset(x, y, z), CEBattery.LEAD_ACID_CASING.get());
    }

    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void aBankIsASolidBoxOfOneChemistryUnderItsTerminal(GameTestHelper h) {
        box(h, new BlockPos(1, 1, 1), 2, 2, 3);
        var terminal = new BlockPos(1, 3, 1);
        h.setBlock(terminal, CEBattery.LEAD_ACID_TERMINAL.get());
        var level = h.getLevel();
        var at = h.absolutePos(terminal);
        h.assertTrue(BatteryStructure.find(level, at, BatteryChemistry.LEAD_ACID) == 12, "A 2x2x3 box counts its twelve casings");
        h.assertTrue(BatteryStructure.find(level, at, BatteryChemistry.LITHIUM_ION) == BatteryStructure.NO_CASING, "Casings of the other chemistry are not a bank");
        h.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        h.assertTrue(BatteryStructure.find(level, at, BatteryChemistry.LEAD_ACID) == BatteryStructure.NOT_A_BOX, "A hole leaves a box that is not solid");
        h.setBlock(new BlockPos(2, 2, 2), CEBattery.LEAD_ACID_CASING.get());
        h.setBlock(new BlockPos(1, 4, 1), CEBattery.LEAD_ACID_CASING.get());
        h.assertTrue(BatteryStructure.find(level, at, BatteryChemistry.LEAD_ACID) == 12, "A block above the terminal is not part of the box");
        h.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void aBankJoinsTheCircuitOnlyWithAFullElectrolyte(GameTestHelper h) {
        box(h, new BlockPos(1, 1, 1), 2, 2, 2);
        var terminal = new BlockPos(1, 3, 1);
        h.setBlock(terminal, CEBattery.LEAD_ACID_TERMINAL.get());
        h.runAfterDelay(60, () -> {
            var bank = (BatteryTerminalBlockEntity) h.getBlockEntity(terminal);
            h.assertTrue(bank.casings() == 8, "The terminal found its eight casings");
            h.assertTrue(!bank.ready(h.getLevel().getGameTime()), "An empty bank is not in the circuit");
            int accepted = bank.electrolyte().fill(new FluidStack(net.createplant.registry.CEMetals.DILUTE_SULFURIC_ACID.get(), 100_000), IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(accepted == 8 * BatteryChemistry.ELECTROLYTE_PER_CASING, "The tank takes 250 mB per casing: " + accepted);
            h.assertTrue(bank.electrolyte().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 0, "Only its own electrolyte is accepted");
            h.assertTrue(bank.ready(h.getLevel().getGameTime()), "A full bank is in the circuit");
            h.assertTrue(bank.emf() == 0 && bank.ohms() == 5, "An empty charge gives no voltage and eight casings 5 ohms");
            h.succeed();
        });
    }

    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void acidInAVesselBurnsUntilDeathAndMilkDoesNotCureIt(GameTestHelper h) {
        var mob = h.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5, 2, 2.5));
        CorrosiveContact.soak(mob, new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
        h.assertTrue(!mob.hasEffect(CESafety.ACID_BURN), "Water does not burn");
        CorrosiveContact.soak(mob, new FluidStack(CEUranium.SULFURIC_ACID.get(), 1000));
        var burn = mob.getEffect(CESafety.ACID_BURN);
        h.assertTrue(burn != null && burn.isInfiniteDuration(), "Acid gives an effect that never runs out");
        mob.removeEffectsCuredBy(EffectCures.MILK);
        h.assertTrue(mob.hasEffect(CESafety.ACID_BURN), "Milk does not cure it");
        h.succeed();
    }
}
