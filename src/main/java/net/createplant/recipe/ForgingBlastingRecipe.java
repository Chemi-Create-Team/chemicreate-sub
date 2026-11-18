package net.createplant.recipe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;

import net.createplant.registry.CERecipeTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ForgingBlastingRecipe extends StandardProcessingRecipe<SingleRecipeInput>
        implements IAssemblyRecipe {
    public ForgingBlastingRecipe(ProcessingRecipeParams params) {
        super(CERecipeTypes.FORGING_BLASTING, params);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return !input.isEmpty() && ingredients.getFirst().test(input.getItem(0));
    }

    @Override
    public Component getDescriptionForAssembly() {
        return Component.translatable("recipe.create_refinery_n_reactors.assembly.blasting");
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> machines) {
        machines.add(AllBlocks.ENCASED_FAN.get());
        machines.add(Items.LAVA_BUCKET);
    }

    @Override
    public void addAssemblyIngredients(List<Ingredient> ingredients) {}

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> net.createplant.compat.jei.ForgingBlastingSubCategory::new;
    }
}
