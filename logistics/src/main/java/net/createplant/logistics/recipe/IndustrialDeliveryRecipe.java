package net.createplant.logistics.recipe;
import net.createplant.logistics.registry.CELogisticsRecipeTypes;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

public class IndustrialDeliveryRecipe extends StandardProcessingRecipe<RecipeInput> {
    public IndustrialDeliveryRecipe(ProcessingRecipeParams params) { super(CELogisticsRecipeTypes.INDUSTRIAL_DELIVERY, params); }
    @Override protected int getMaxInputCount() { return 256; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override protected int getMaxFluidInputCount() { return 1; }
    @Override protected int getMaxFluidOutputCount() { return 0; }
    @Override protected boolean canSpecifyDuration() { return true; }
    @Override public boolean matches(RecipeInput input, Level level) { return false; }
}
