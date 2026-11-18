package net.createplant.recipe;

import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;

import net.createplant.registry.CEItems;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;

/** A folding press releases its secondary output only when the assembly finishes. */
public final class ForgingPressingRecipe extends PressingRecipe {
    public ForgingPressingRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CERecipeTypes.FORGING_PRESSING_SERIALIZER.get();
    }

    @Override
    public net.minecraft.network.chat.Component getDescriptionForAssembly() {
        return net.minecraft.network.chat.Component.translatable(
                "recipe.create_refinery_n_reactors.assembly.folding");
    }

    @Override
    public java.util.function.Supplier<
                    java.util.function.Supplier<
                            com.simibubi
                                    .create
                                    .compat
                                    .jei
                                    .category
                                    .sequencedAssembly
                                    .SequencedAssemblySubCategory>>
            getJEISubCategory() {
        return () -> net.createplant.compat.jei.ForgingPressingSubCategory::new;
    }

    @Override
    public List<ItemStack> rollResults(List<ProcessingOutput> outputs, RandomSource random) {
        var result = super.rollResults(outputs, random);
        if (!result.isEmpty() && result.getFirst().is(CEItems.HEATED_UNFINISHED_STEEL.get()))
            result.removeIf(stack -> stack.is(CEItems.STEEL_SLAG.get()));
        return result;
    }
}
