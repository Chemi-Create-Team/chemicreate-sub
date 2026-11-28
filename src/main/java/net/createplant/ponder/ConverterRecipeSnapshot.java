package net.createplant.ponder;

import java.util.ArrayList;
import java.util.List;
import net.createplant.recipe.ConverterRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Lets the manufacturing Ponder read the converter's recipes, the way it reads every other recipe. */
public final class ConverterRecipeSnapshot {
    private ConverterRecipeSnapshot() { }

    public static void register() {
        ManufacturingRecipe.register(recipe -> recipe instanceof ConverterRecipe converter ? resolve(converter) : null);
    }

    private static ManufacturingRecipe resolve(ConverterRecipe converter) {
        var feeds = new ArrayList<FluidStack>();
        feeds.add(converter.gasIngredient().getFluids()[0]);
        converter.metalIngredient().ifPresent(metal -> feeds.add(metal.getFluids()[0]));
        var products = java.util.stream.Stream.of(converter.metalResult(), converter.byproduct())
            .filter(fluid -> !fluid.isEmpty()).toList();
        return new ManufacturingRecipe(converter.itemIngredients().stream().map(ingredient -> ingredient.getItems()[0].copy()).toList(),
            feeds, List.of(converter.result()), products,
            ItemStack.EMPTY, net.createplant.chemistry.ReactorModule.NONE, com.simibubi.create.content.processing.recipe.HeatCondition.NONE);
    }
}
