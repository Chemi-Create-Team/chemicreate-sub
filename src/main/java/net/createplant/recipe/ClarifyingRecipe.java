package net.createplant.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

public final class ClarifyingRecipe extends StandardProcessingRecipe<RecipeInput> {
    public ClarifyingRecipe(ProcessingRecipeParams params) { super(CERecipeTypes.CLARIFYING, params); }
    @Override protected int getMaxInputCount() { return 0; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected int getMaxFluidInputCount() { return 1; }
    @Override protected int getMaxFluidOutputCount() { return 1; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
}
