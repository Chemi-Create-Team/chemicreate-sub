package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CESharedChemicals;
import net.createplant.safety.CorrosiveContact;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CorrosiveContactGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 20)
    public static void acidContactRespectsTheLiquidSurfaceAndDoesNotStack(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var acid = CESharedChemicals.SULFURIC_ACID.get().defaultFluidState();
        var mob = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(2.5, 2.95, 2.5));
        mob.setNoGravity(true);
        mob.tickCount = (20 - mob.getId() % 20) % 20;
        mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        float before = mob.getHealth();
        CorrosiveContact.touch(level, pos, mob, acid);
        helper.assertTrue(mob.getHealth() == before, "A body above the liquid surface is not burned");
        mob.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        CorrosiveContact.touch(level, pos, mob, acid);
        helper.assertTrue(mob.getHealth() == before - 2, "Acid bypasses full diamond armour and deals exactly two health points");
        CorrosiveContact.touch(level, pos, mob, acid);
        CorrosiveContact.touch(level, pos, mob, CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState());
        helper.assertTrue(mob.getHealth() == before - 2, "Multiple acid or alkali cells in the same tick do not multiply the damage");
        helper.assertTrue(!CorrosiveContact.harmful(Fluids.WATER.defaultFluidState())
                && !CorrosiveContact.harmful(CEChlorAlkali.BRINE.get().defaultFluidState())
                && !CorrosiveContact.harmful(CEFluids.HYDROGEN_GAS.get().defaultFluidState()),
            "Water, salt water and gas are not acid or alkali contact burns");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 70)
    public static void sourceAndFlowingAlkaliDamageLivingThingsOverTime(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 2, 2), flowing = new BlockPos(5, 2, 2);
        for (BlockPos pos : new BlockPos[]{source, flowing}) {
            helper.setBlock(pos.below(), Blocks.STONE);
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) helper.setBlock(pos.relative(direction), Blocks.STONE);
        }
        helper.setBlock(source, CEFluids.NITRIC_ACID.get().defaultFluidState().createLegacyBlock());
        helper.setBlock(flowing, CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock().setValue(LiquidBlock.LEVEL, 3));
        helper.setBlock(flowing.above(), CEChlorAlkali.CAUSTIC_SODA.get().defaultFluidState().createLegacyBlock());
        var acidMob = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(1.5, 2.05, 2.5));
        var alkaliMob = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(5.5, 2.05, 2.5));
        acidMob.setNoGravity(true);
        alkaliMob.setNoGravity(true);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(acidMob.getHealth() <= acidMob.getMaxHealth() - 4,
                "A living thing in an acid source takes recurring damage: " + acidMob.getHealth());
            helper.assertTrue(alkaliMob.getHealth() <= alkaliMob.getMaxHealth() - 4,
                "A living thing in flowing alkali takes recurring damage: " + alkaliMob.getHealth());
            helper.succeed();
        });
    }
}
