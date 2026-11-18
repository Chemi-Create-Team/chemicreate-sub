package net.createplant.mixin;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createplant.recipe.ForgingBlastingRecipe;
import net.createplant.registry.CEItems;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Native blasting has no assembly lookup; other items retain Create's ordinary recipes. */
@Mixin(value = AllFanProcessingTypes.BlastingType.class, remap = false)
public class ForgingBlastingMixin {
    @Inject(method = "canProcess", at = @At("HEAD"), cancellable = true)
    private void forgingCanProcess(
            ItemStack stack, Level level, CallbackInfoReturnable<Boolean> ci) {
        if (stack.is(CEItems.HEATED_UNFINISHED_STEEL.get()))
            ci.setReturnValue(
                    SequencedAssemblyRecipe.getRecipe(
                                    level,
                                    stack,
                                    CERecipeTypes.FORGING_BLASTING.getType(),
                                    ForgingBlastingRecipe.class)
                            .isPresent());
    }

    @Inject(method = "process", at = @At("HEAD"), cancellable = true)
    private void forgingProcess(
            ItemStack stack, Level level, CallbackInfoReturnable<List<ItemStack>> ci) {
        if (!stack.is(CEItems.HEATED_UNFINISHED_STEEL.get())) return;
        var recipe =
                SequencedAssemblyRecipe.getRecipe(
                        level,
                        stack,
                        CERecipeTypes.FORGING_BLASTING.getType(),
                        ForgingBlastingRecipe.class);
        ci.setReturnValue(
                recipe.map(
                                holder ->
                                        RecipeApplier.applyRecipeOn(
                                                level, stack, holder.value(), false))
                        .orElse(null));
    }
}
