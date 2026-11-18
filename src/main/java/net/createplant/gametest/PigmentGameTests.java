package net.createplant.gametest;

import com.simibubi.create.AllRecipeTypes;
import net.createplant.CreatePlant;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEMetals;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.gametest.*;

/** Mineral pigments ground into dyes on Create's millstone. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class PigmentGameTests {
    private static ItemStack milled(GameTestHelper h, ItemLike pigment) {
        var input = new SingleRecipeInput(new ItemStack(pigment));
        return h.getLevel().getRecipeManager().getRecipeFor(AllRecipeTypes.MILLING.<SingleRecipeInput, Recipe<SingleRecipeInput>>getType(), input, h.getLevel())
            .map(holder -> holder.value().getResultItem(h.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }
    @GameTest(template = "test_reactor")
    public static void everyMineralPigmentGrindsIntoFourDyes(GameTestHelper h) {
        var white = milled(h, CEMetals.TITANIUM_DIOXIDE.get());var green = milled(h, CEMetals.CHROMIUM_OXIDE.get());
        h.assertTrue(white.is(Items.WHITE_DYE) && white.getCount() == 4, "Titanium dioxide grinds into four white dyes: " + white);
        h.assertTrue(green.is(Items.GREEN_DYE) && green.getCount() == 4, "Chromium oxide grinds into four green dyes: " + green);
        ItemLike[] pigments = {CEBlocks.TRIIRON_TETROXIDE_PILE.get(), CEMetals.IRON_OXIDE_RED.get(), CEMetals.NICKEL_TITANIUM_YELLOW.get(), CEMetals.MOLYBDENUM_BLUE.get()};
        Item[] dyes = {Items.BLACK_DYE, Items.RED_DYE, Items.YELLOW_DYE, Items.BLUE_DYE};
        for (int i = 0; i < pigments.length; i++) {
            var dye = milled(h, pigments[i]);
            h.assertTrue(dye.is(dyes[i]) && dye.getCount() == 4, pigments[i] + " grinds into four " + dyes[i] + ": " + dye);
        }
        h.succeed();
    }
}
