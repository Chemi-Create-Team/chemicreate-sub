package net.createplant.mixin;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Stream;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.createplant.CreatePlant;
import net.createplant.compat.jei.CreatePlantJeiPlugin;
import net.createplant.compat.jei.PlantProcess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** JEI sorts tabs independently of registered recipes. Order production before bucket emptying for our fluids. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class JeiProductionCategoryMixin {
    @Shadow public abstract <T> Stream<T> getRecipesStream(RecipeType<T> type, IFocusGroup focuses, boolean includeHidden);

    @Inject(method = "getRecipeCategoriesUncached", at = @At("RETURN"), cancellable = true, require = 0)
    private void createPlant$productionFirst(Collection<IRecipeCategory<?>> limited, IFocusGroup focuses, boolean includeHidden,
        CallbackInfoReturnable<Stream<IRecipeCategory<?>>> cir) {
        var outputs = RecipeIngredientRole.OUTPUT;
        if (!focuses.getFocuses(NeoForgeTypes.FLUID_STACK,outputs).anyMatch(focus ->
            BuiltInRegistries.FLUID.getKey(focus.getTypedValue().getIngredient().getFluid()).getNamespace().equals(CreatePlant.MOD_ID))
            && !focuses.getItemStackFocuses(outputs).anyMatch(focus -> focus.getTypedValue().getIngredient().getItem() instanceof BucketItem bucket
                && BuiltInRegistries.FLUID.getKey(bucket.content).getNamespace().equals(CreatePlant.MOD_ID))) return;

        var costs = new HashMap<RecipeType<?>,Integer>();
        cir.setReturnValue(cir.getReturnValue().sorted(Comparator.comparingInt(category -> costs.computeIfAbsent(category.getRecipeType(),type -> {
            var id = type.getUid();
            if (id.getNamespace().equals("create") && (id.getPath().equals("draining") || id.getPath().equals("spout_filling"))) return Integer.MAX_VALUE;
            if (!id.getNamespace().equals(CreatePlant.MOD_ID)) return 0;
            return getRecipesStream(type,focuses,includeHidden).filter(PlantProcess.class::isInstance).map(PlantProcess.class::cast)
                .mapToInt(CreatePlantJeiPlugin::routeCost).min().orElse(Integer.MAX_VALUE - 1);
        }))));
    }
}
