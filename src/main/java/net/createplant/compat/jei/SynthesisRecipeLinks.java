package net.createplant.compat.jei;

import java.util.List;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.createplant.client.SynthesisGuide;
import net.minecraft.world.item.BucketItem;
import net.neoforged.neoforge.fluids.FluidStack;

/** A project's process bucket opens the same recipes as its fluid, including native Create recipes. */
final class SynthesisRecipeLinks implements IRecipeManagerPlugin {
    private IJeiRuntime runtime;

    void runtime(IJeiRuntime runtime) {
        this.runtime = runtime;
    }

    private IFocus<FluidStack> fluidFocus(IFocus<?> focus) {
        if (runtime == null) return null;
        var item = focus.getTypedValue().getItemStack();
        if (item.isEmpty() || !(item.get().getItem() instanceof BucketItem bucket) || !SynthesisGuide.linked(bucket.content))
            return null;
        return runtime.getJeiHelpers().getFocusFactory().createFocus(focus.getRole(), NeoForgeTypes.FLUID_STACK,
            new FluidStack(bucket.content, 1000));
    }

    @Override public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        var fluid = fluidFocus(focus);
        if (fluid == null) return List.of();
        List<RecipeType<?>> types = new java.util.ArrayList<>();
        runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(fluid)).get()
            .forEach(category -> types.add(category.getRecipeType()));
        return types;
    }

    @Override public <T, V> List<T> getRecipes(IRecipeCategory<T> category, IFocus<V> focus) {
        var fluid = fluidFocus(focus);
        if (fluid == null) return List.of();
        return runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).limitFocus(List.of(fluid)).get().toList();
    }

    @Override public <T> List<T> getRecipes(IRecipeCategory<T> category) {
        return List.of();
    }
}
