package net.createplant.gametest;

import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.CharredWoodBlock;
import net.createplant.block.entity.CharredWoodBlockEntity;
import net.createplant.chemistry.WoodCarbonization;
import net.createplant.registry.CECarbonization;
import net.createplant.registry.CEUranium;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WoodCarbonizationGameTests {
    private static List<ItemStack> drops(GameTestHelper h, BlockPos pos) {
        return Block.getDrops(h.getBlockState(pos), h.getLevel(), h.absolutePos(pos), h.getBlockEntity(pos));
    }

    @GameTest(template = "test_reactor", timeoutTicks = 650)
    public static void aLogBecomesCharcoalAfterThirtySeconds(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        h.assertTrue(WoodCarbonization.tryStart(h.getLevel(), h.absolutePos(pos)), "A log accepts one sulfuric acid dose");
        h.assertTrue(h.getBlockState(pos).getValue(CharredWoodBlock.STAGE) == 0
                && h.getBlockState(pos).getValue(CharredWoodBlock.AXIS) == Direction.Axis.X,
            "The first stage keeps the original log axis");
        h.runAfterDelay(590, () -> h.assertTrue(h.getBlockState(pos).getValue(CharredWoodBlock.STAGE) < 3,
            "The dose cannot finish before thirty seconds"));
        h.runAfterDelay(610, () -> {
            h.assertTrue(h.getBlockState(pos).getValue(CharredWoodBlock.STAGE) == 3, "Thirty seconds complete carbonization");
            List<ItemStack> drops = drops(h, pos);
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.CHARCOAL) && drops.getFirst().getCount() == 4,
                "One completed log drops four charcoal");
            h.succeed();
        });
    }

    @GameTest(template = "test_reactor")
    public static void oneAcidSourceOnlyCarbonizesOneNeighbour(GameTestHelper h) {
        BlockPos source = new BlockPos(10, 3, 10);
        h.setBlock(source.west(), Blocks.OAK_LOG);
        h.setBlock(source.east(), Blocks.OAK_PLANKS);
        h.setBlock(source, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock());
        WoodCarbonization.tryFromSource(h.getLevel(), h.absolutePos(source));
        int count = 0;
        for (BlockPos neighbour : List.of(source.west(), source.east()))
            if (h.getBlockState(neighbour).is(CECarbonization.CHARRED_WOOD.get())) count++;
        h.assertTrue(count == 1 && h.getBlockState(source).getFluidState().isEmpty(), "One acid source is consumed by exactly one neighbour");
        h.assertTrue(!WoodCarbonization.tryFromSource(h.getLevel(), h.absolutePos(source)), "The consumed source cannot react again");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void flowingAcidAndNonWoodCannotStartCarbonization(GameTestHelper h) {
        BlockPos flowing = new BlockPos(4, 3, 4), source = new BlockPos(12, 3, 4);
        h.setBlock(flowing.west(), Blocks.OAK_LOG);
        h.setBlock(flowing, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock().setValue(LiquidBlock.LEVEL, 1));
        h.assertTrue(!WoodCarbonization.tryFromSource(h.getLevel(), h.absolutePos(flowing))
                && h.getBlockState(flowing.west()).is(Blocks.OAK_LOG), "Flowing acid cannot process a wood block");
        h.setBlock(source, CEUranium.SULFURIC_ACID.get().defaultFluidState().createLegacyBlock());
        h.assertTrue(!WoodCarbonization.tryFromSource(h.getLevel(), h.absolutePos(source)) && h.getBlockState(source).getFluidState().isSource(),
            "A source with no wood is not consumed");
        h.assertTrue(!WoodCarbonization.tryStart(h.getLevel(), h.absolutePos(source.below())), "Stone and air are not wood feedstock");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void aSavedPartialPlankFinishesWithOneCharcoal(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, Blocks.BIRCH_PLANKS);
        WoodCarbonization.tryStart(h.getLevel(), h.absolutePos(pos));
        var before = (CharredWoodBlockEntity) h.getBlockEntity(pos);
        for (int i = 0; i < 12; i++) before.advance();
        var state = h.getBlockState(pos);
        var saved = before.saveWithoutMetadata(h.getLevel().registryAccess());
        h.setBlock(pos, Blocks.AIR);
        h.setBlock(pos, state);
        var restored = (CharredWoodBlockEntity) h.getBlockEntity(pos);
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.remainingTicks() == 360 && restored.originalItem().is(Items.BIRCH_PLANKS),
            "Partial progress and the exact original plank survive reload");
        for (int i = 0; i < 18; i++) restored.advance();
        List<ItemStack> drops = drops(h, pos);
        h.assertTrue(h.getBlockState(pos).getValue(CharredWoodBlock.STAGE) == 3 && drops.size() == 1
                && drops.getFirst().is(Items.CHARCOAL) && drops.getFirst().getCount() == 1,
            "The restored plank only finishes its remaining dose and drops one charcoal");
        h.assertTrue(!WoodCarbonization.tryStart(h.getLevel(), h.absolutePos(pos)), "Charred wood cannot be fed another dose");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void earlyBreakReturnsWoodAndPlacedCharredWoodIsFinished(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 2, 4);
        h.setBlock(pos, Blocks.SPRUCE_LOG);
        WoodCarbonization.tryStart(h.getLevel(), h.absolutePos(pos));
        List<ItemStack> early = drops(h, pos);
        h.assertTrue(early.size() == 1 && early.getFirst().is(Items.SPRUCE_LOG) && early.getFirst().getCount() == 1,
            "Breaking an unfinished dose returns the original wood rather than early charcoal");
        h.setBlock(pos, CECarbonization.CHARRED_WOOD.get());
        h.assertTrue(h.getBlockState(pos).getValue(CharredWoodBlock.STAGE) == 3
                && !h.getBlockState(pos).getValue(CharredWoodBlock.LOGS),
            "An ordinarily placed charred-wood item is finished and never recreates a log-size dose");
        h.assertTrue(WoodCarbonization.accepts(Blocks.CRIMSON_STEM.defaultBlockState())
                && WoodCarbonization.accepts(Blocks.WARPED_PLANKS.defaultBlockState())
                && WoodCarbonization.accepts(Blocks.BAMBOO_PLANKS.defaultBlockState()),
            "Log and plank tags include Nether and bamboo wood as organic feedstock");
        h.succeed();
    }
}
