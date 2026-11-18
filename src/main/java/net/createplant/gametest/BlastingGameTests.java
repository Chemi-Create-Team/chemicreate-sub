package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.block.BlastingChargeBlock;
import net.createplant.registry.CEBlasting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The blasting charge: a redstone signal lights it, the rock in reach falls as items, machines, containers and obsidian stand, and charges in reach go off too. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class BlastingGameTests {
    @GameTest(template = "test_reactor", timeoutTicks = 120)
    public static void aChargeBreaksTheRockInReachIntoItemsAndSetsOffTheNext(GameTestHelper h) {
        BlockPos centre = new BlockPos(16, 4, 16), next = centre.north(3), chest = centre.east(3), obsidian = centre.west(3), ore = centre.south(2), far = centre.south(4);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-4, -3, -7), centre.offset(4, 3, 4))) h.setBlock(pos, Blocks.STONE);
        h.setBlock(ore, Blocks.IRON_ORE);h.setBlock(chest, Blocks.CHEST);h.setBlock(obsidian, Blocks.OBSIDIAN);
        h.setBlock(next, CEBlasting.BLASTING_CHARGE.get());h.setBlock(centre, CEBlasting.BLASTING_CHARGE.get());
        h.setBlock(centre.above(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(h.getBlockState(centre).getValue(BlastingChargeBlock.LIT), "A redstone signal lights the fuse");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, "mixing/anfo")).isPresent(), "The ANFO mixing recipe loads");
        h.runAfterDelay(BlastingChargeBlock.FUSE - 5, () -> h.assertTrue(h.getBlockState(centre).is(CEBlasting.BLASTING_CHARGE.get()), "Nothing goes off before the fuse has burnt"));
        h.runAfterDelay(BlastingChargeBlock.FUSE + BlastingChargeBlock.CHAIN_FUSE + 10, () -> {
            h.assertTrue(h.getBlockState(centre).isAir() && h.getBlockState(ore).isAir() && h.getBlockState(centre.below(2)).isAir(), "The rock within three blocks is gone");
            h.assertTrue(h.getBlockState(next).isAir() && h.getBlockState(next.north(2)).isAir(), "The charge in reach went off and broke the rock around it");
            h.assertTrue(h.getBlockState(chest).is(Blocks.CHEST) && h.getBlockState(obsidian).is(Blocks.OBSIDIAN) && h.getBlockState(far).is(Blocks.STONE),
                "Containers, obsidian and rock out of reach stand");
            var items = h.getEntities(EntityType.ITEM);
            h.assertTrue(items.stream().anyMatch(item -> item.getItem().is(Items.RAW_IRON)) && items.stream().anyMatch(item -> item.getItem().is(Items.COBBLESTONE)),
                "The ore and the stone drop as mined: " + items.size() + " stacks");
            h.succeed();
        });
    }
}
