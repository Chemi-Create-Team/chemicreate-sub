package net.createplant.recipe;
import net.createplant.registry.CEAtomicsRecipeTypes;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

public final class CubicPressRecipe extends StandardProcessingRecipe<CubicPressRecipe.Input> {
    public record Input(ItemStack feed, ItemStack catalyst) implements RecipeInput {
        @Override public ItemStack getItem(int slot) { return slot == 0 ? feed : catalyst; }
        @Override public int size() { return 2; }
    }
    public CubicPressRecipe(ProcessingRecipeParams params) { super(CEAtomicsRecipeTypes.CUBIC_PRESSING, params); }
    @Override protected int getMaxInputCount() { return 2; }
    @Override protected int getMaxOutputCount() { return 2; }
    @Override protected boolean canRequireHeat() { return true; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override public boolean matches(Input input, Level level) {
        return ingredients.size() == 2 && ingredients.get(0).test(input.feed()) && ingredients.get(1).test(input.catalyst());
    }
}
