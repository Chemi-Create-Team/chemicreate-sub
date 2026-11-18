package net.createplant.gametest;

import net.createplant.CreatePlant;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class ManualGameTests {
    private static final String[] GUIDE = {"root", "dispatch", "steel", "vessel", "electrolysis", "sulfuric_acid", "leaching", "zirconia", "aluminum", "nickel",
        "stainless_steel", "power", "nuclear", "fusion", "plastic", "compressor", "fertilizer", "mining"};

    @GameTest(template = "test_reactor")
    public static void theManualCraftsAsATranslatedBookAndTheGuideTreeLoads(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(CreatePlant.location("manual")).orElseThrow().value();
        var book = recipe.getResultItem(h.getLevel().registryAccess());
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(book.is(Items.WRITTEN_BOOK) && content != null && content.pages().size() == 23
            && content.pages().getFirst().raw().getContents() instanceof TranslatableContents page && page.getKey().equals("book." + CreatePlant.MOD_ID + ".manual.page_0"),
            "A book and an iron ingot make the twenty-three-page manual, its pages translated");
        for (String step : GUIDE)
            h.assertTrue(server.getAdvancements().get(CreatePlant.location("guide/" + step)) != null, "The guide advancement loads: " + step);
        var root = server.getAdvancements().get(CreatePlant.location("guide/root")).value();
        h.assertTrue(root.rewards().loot().stream().anyMatch(table -> table.location().equals(CreatePlant.location("gameplay/manual"))),
            "Joining grants the guide's root, which hands out the manual");
        h.succeed();
    }
}
