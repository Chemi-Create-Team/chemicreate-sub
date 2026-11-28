package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import static net.createplant.gametest.CraftingRecipeGameTests.*;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import net.createplant.recipe.ConverterRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static net.createplant.gametest.SurvivalReachabilityGameTests.has;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CraftingRecipeAtomicsGameTests {
    @GameTest(template = "test_reactor")
    public static void steelUsesPuddlingAndConverterWhileIndustrialIronKeepsItsStandardRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var access = h.getLevel().registryAccess();
        var industrialIron = AllBlocks.INDUSTRIAL_IRON_BLOCK.asItem();
        var standard = manager.byKey(ResourceLocation.parse("create:industrial_iron_block_from_ingots_iron_stonecutting")).orElseThrow().value();
        h.assertTrue(standard.getType() == RecipeType.STONECUTTING && contains(standard, Items.IRON_INGOT)
            && standard.getResultItem(access).is(industrialIron) && standard.getResultItem(access).getCount() == 2,
            "Create's original one-iron-ingot to two-industrial-iron recipe is restored");
        h.assertTrue(manager.byKey(ResourceLocation.parse("create:weathered_iron_block_from_ingots_iron_stonecutting")).isPresent()
            && manager.byKey(CreatePlant.location("sequenced_assembly/industrial_iron_block")).isEmpty(),
            "Create's weathered recipe is restored and the replaced industrial-iron assembly is removed");
        var unpacking = CreatePlant.location("steel_ingot_from_steel_block");
        var makers = manager.getRecipes().stream()
            .filter(holder -> holder.value().getResultItem(access).is(CEItems.STEEL_INGOT.get()) && !holder.id().equals(unpacking))
            .filter(holder -> !(holder.value() instanceof net.createplant.recipe.ConverterRecipe charge) || !charge.legacy())
            .toList();
        h.assertTrue(makers.size() == 3, "Steel has carbon mixing, forging and cast-slab production recipes: "
            + makers.stream().map(RecipeHolder::id).toList());
        var assembly = makers.stream().filter(holder -> holder.id().equals(
            CreatePlant.location("sequenced_assembly/steel_ingot"))).findFirst().orElseThrow();
        h.assertTrue(assembly.value().getType() == AllRecipeTypes.SEQUENCED_ASSEMBLY.getType(),
            "The small-batch route is the five-pass carbon-steel assembly");
        var charge = (net.createplant.recipe.ConverterRecipe) manager.byKey(CreatePlant.location("converter/refine_hot_metal"))
            .orElseThrow().value();
        h.assertTrue(charge.itemIngredients().size() == 2 && charge.itemIngredients().get(0).count() == 8
            && charge.itemIngredients().get(1).count() == 4 && contains(charge, Items.IRON_INGOT) && contains(charge, CEItems.QUICKLIME.get())
            && charge.metalIngredient().orElseThrow().amount() == 6_400 && charge.metalResult().getAmount() == 6_400,
            "Bulk steel consumes pretreated hot metal, iron and quicklime and is tapped as a liquid");
        for (String suffix : List.of("", "_with_flux")) for (String feed : List.of("iron_ore", "raw_iron"))
            h.assertTrue(manager.byKey(CreatePlant.location("mixing/puddled_iron_from_" + feed + suffix)).isEmpty(), "Puddling recipes are removed");
        var mixing = (com.simibubi.create.content.kinetics.mixer.MixingRecipe) manager.byKey(CreatePlant.location("mixing/unprocessed_steel")).orElseThrow().value();
        h.assertTrue(mixing.getIngredients().stream().filter(ingredient -> ingredient.test(Items.IRON_INGOT.getDefaultInstance())).count() == 4
            && contains(mixing, CEItems.CARBON_POWDER.get()) && mixing.getResultItem(access).is(CEItems.UNPROCESSED_STEEL.get()) && mixing.getResultItem(access).getCount() == 4
            && mixing.getRequiredHeat() == com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED, "Four iron ingots and carbon powder make four workpieces only with superheating");

        var removed = CreatePlant.location("industrial_steel_stock");
        h.assertTrue(!BuiltInRegistries.ITEM.containsKey(removed), "The obsolete industrial steel stock is not registered");
        for (var holder : manager.getRecipes()) {
            var result = holder.value().getResultItem(access);
            h.assertTrue(result.isEmpty() || !BuiltInRegistries.ITEM.getKey(result.getItem()).equals(removed),
                holder.id() + " must not produce industrial_steel_stock");
            for (var ingredient : holder.value().getIngredients())
                for (var stack : ingredient.getItems())
                    h.assertTrue(!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(removed),
                        holder.id() + " must not consume industrial_steel_stock");
        }
        h.succeed();
    }

}
