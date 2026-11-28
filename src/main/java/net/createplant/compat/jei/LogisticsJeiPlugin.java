package net.createplant.compat.jei;

import net.createplant.registry.CELogisticsRecipeTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.createplant.CreatePlant;
import net.createplant.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import static net.createplant.compat.jei.CreatePlantJeiPlugin.*;
import net.createplant.compat.jei.CreatePlantJeiPlugin.Kind;

@JeiPlugin
public class LogisticsJeiPlugin implements IModPlugin {
    private static List<Kind> kinds;

    private static List<Kind> kinds() {
        if (kinds == null) kinds = List.of(
            new Kind("industrial_delivery", "plant/industrial_dispatch", CEIndustrialExpansion.DISPATCH.get(), CEIndustrialExpansion.DISPATCH.get(), AllBlocks.ITEM_VAULT.get()),
            new Kind("arm_assembly", "plant/industrial_arm", net.createplant.registry.CEArm.ARM_ITEM.get(), net.createplant.registry.CEArm.ARM_ITEM.get(),
                net.createplant.registry.CEArm.WELDING_TORCH.get(), AllBlocks.DEPOT.get()));
        return kinds;
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) { addCategories(registration, kinds()); }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) { addCatalysts(registration, kinds()); }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var world = Minecraft.getInstance().level;
        if (world != null) registration.addRecipes(type("arm_assembly"), net.createplant.arm.ArmRecipe.all(world).stream().map(holder -> armAssembly(holder.value())).toList());
        registration.addRecipes(type("industrial_delivery"), deliveries());
    }

    /** The item put on the depot and every part the steps use up go in; the torch is kept; the note lists the steps in order. */
    private static PlantProcess armAssembly(net.createplant.arm.ArmRecipe recipe) {
        List<List<ItemStack>> in = new java.util.ArrayList<>();
        in.add(List.of(new ItemStack(recipe.input())));
        var steps = Component.empty();
        for (int i = 0; i < recipe.steps().size(); i++) {
            var step = recipe.steps().get(i);
            step.consume().ifPresent(part -> in.add(List.of(new ItemStack(part.item(), part.count()))));
            if (i > 0) steps.append(" > ");
            steps.append(Component.translatable("jei." + CreatePlant.MOD_ID + ".arm_step." + step.pattern().id()));
            if (step.helpers() > 0) steps.append(Component.translatable("jei." + CreatePlant.MOD_ID + ".arm_step.helped"));
        }
        List<ItemStack> kept = recipe.usesTorch() ? List.of(net.createplant.registry.CEArm.WELDING_TORCH.toStack()) : List.of();
        return new PlantProcess(in, List.of(), kept, List.of(recipe.result().copy()), List.of(), note("arm_assembly", steps));
    }

    private static List<PlantProcess> deliveries() {
        var level = Minecraft.getInstance().level;if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(CELogisticsRecipeTypes.INDUSTRIAL_DELIVERY.<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.IndustrialDeliveryRecipe>getType()).stream().map(holder -> {
            var recipe = holder.value();var inputs = new java.util.LinkedHashMap<String, List<ItemStack>>();
            for (var ingredient : recipe.getIngredients()) {
                var options = Arrays.asList(ingredient.getItems());
                String key = options.stream().map(stack -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().collect(java.util.stream.Collectors.joining(","));
                if (!inputs.containsKey(key)) inputs.put(key, options.stream().map(ItemStack::copy).toList());
                else inputs.get(key).forEach(stack -> stack.grow(1));
            }
            return new PlantProcess(List.copyOf(inputs.values()), recipe.getFluidIngredients().stream().map(input -> input.getFluids()[0]).toList(), List.of(),
                recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList(), List.of(), note("industrial_delivery", recipe.getProcessingDuration())).withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
        }).toList();
    }

    @Override
    public ResourceLocation getPluginUid() {
        return CreatePlant.location("logistics_jei_plugin");
    }
}
