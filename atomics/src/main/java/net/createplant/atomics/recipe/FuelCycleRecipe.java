package net.createplant.atomics.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

public class FuelCycleRecipe extends StandardProcessingRecipe<RecipeInput> {
    public FuelCycleRecipe(IRecipeTypeInfo type, ProcessingRecipeParams params) { super(type, params); }
    @Override protected int getMaxInputCount() { return 8; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected int getMaxFluidInputCount() { return 2; }
    @Override protected int getMaxFluidOutputCount() { return 4; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
}
