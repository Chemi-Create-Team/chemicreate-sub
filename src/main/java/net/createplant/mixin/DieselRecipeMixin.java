package net.createplant.mixin;

import java.util.Map;
import com.google.gson.JsonElement;
import net.createplant.compat.diesel.DieselIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RecipeManager.class,remap=false)
public class DieselRecipeMixin {
    @Inject(method="apply",at=@At("HEAD"))
    private void createPlant$refiningRecipes(Map<ResourceLocation,JsonElement> recipes,ResourceManager resources,ProfilerFiller profiler,CallbackInfo ci) {
        recipes.entrySet().removeIf(entry -> DieselIntegration.disabledRecipe(entry.getValue()));
    }
}
