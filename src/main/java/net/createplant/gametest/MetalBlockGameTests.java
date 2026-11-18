package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.createplant.registry.CEMetals;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The stainless steel and titanium blocks, and, with Create: Deep Seas installed, the depth their hull holds. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class MetalBlockGameTests {
    @GameTest(template = "test_empty")
    public static void theHullBlocksStoreTheirMetalAndHoldDeepWater(GameTestHelper h) {
        Block stainless = BuiltInRegistries.BLOCK.get(CreatePlant.location("stainless_steel_block")), titanium = BuiltInRegistries.BLOCK.get(CreatePlant.location("titanium_block"));
        h.assertTrue(stainless.asItem() == CEMetals.STAINLESS_STEEL_BLOCK.get() && titanium.asItem() == CEMetals.TITANIUM_BLOCK.get(), "Both blocks are registered with their items");
        var storage = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "storage_blocks"));
        h.assertTrue(stainless.defaultBlockState().is(storage) && titanium.defaultBlockState().is(storage), "They are storage blocks");
        for (String id : new String[]{"stainless_steel_block", "titanium_block", "stainless_steel_ingot_from_stainless_steel_block", "titanium_ingot_from_titanium_block"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location(id)).isPresent(), "Recipe loads: " + id);
        if (ModList.get().isLoaded("create_submarine")) {
            int[] depths = DeepSeasHulls.baseDepths(stainless, titanium);
            h.assertTrue(depths[0] == 600 && depths[1] == 1200, "Create: Deep Seas holds stainless steel to 600 blocks and titanium to 1200: " + depths[0] + " / " + depths[1]);
            org.slf4j.LoggerFactory.getLogger(MetalBlockGameTests.class).info("Create: Deep Seas base depth: stainless steel {}, titanium {}", depths[0], depths[1]);
        }
        h.succeed();
    }

    /** Touches Create: Deep Seas classes; loaded only when that mod is. */
    private static final class DeepSeasHulls {
        static int[] baseDepths(Block... blocks) {
            int[] depths = new int[blocks.length];
            for (int i = 0; i < blocks.length; i++)
                depths[i] = com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig.getFor(blocks[i].defaultBlockState()).orElseThrow().maxWaterDepth();
            return depths;
        }
    }
}
