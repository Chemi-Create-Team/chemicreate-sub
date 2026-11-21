package net.createplant.mixin;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.createplant.client.FluidItemTooltips;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.common.util.SafeIngredientUtil", remap = false)
public class JeiFluidTooltipMixin {
    @Inject(method = "getRichTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/ingredients/IIngredientRenderer;Lmezz/jei/api/ingredients/ITypedIngredient;)V",
        at = @At("RETURN"), require = 0)
    private static void createPlant$describeFluid(ITooltipBuilder tooltip, IIngredientManager ingredients, IIngredientRenderer<?> renderer,
        ITypedIngredient<?> typed, CallbackInfo callback) {
        if (!(typed.getIngredient() instanceof FluidStack fluid)) return;
        tooltip.addAll(FluidItemTooltips.describeFluid(fluid));
        var diagram = FluidItemTooltips.fluidDiagram(fluid);
        if (diagram != null) tooltip.add(diagram);
    }
}
