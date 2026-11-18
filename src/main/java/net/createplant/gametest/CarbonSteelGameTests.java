package net.createplant.gametest;

import java.util.ArrayList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import net.createplant.CreatePlant;
import net.createplant.registry.CEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CarbonSteelGameTests {
    @GameTest(template = "test_forging")
    public static void coalAndCharcoalHaveOneUnambiguousPowderRecipePerMachine(GameTestHelper h) {
        for (var fuel : new Item[]{Items.COAL, Items.CHARCOAL}) for (var type : new AllRecipeTypes[]{AllRecipeTypes.MILLING, AllRecipeTypes.CRUSHING}) {
            var recipes = h.getLevel().getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.value().getType() == type.getType() && holder.value().getIngredients().size() == 1 && holder.value().getIngredients().getFirst().test(fuel.getDefaultInstance())).toList();
            h.assertTrue(recipes.size() == 1, "Coal and charcoal select exactly one recipe per machine: " + type + " / " + fuel + " / " + recipes);
            var recipe = (StandardProcessingRecipe<?>) recipes.getFirst().value();var output = recipe.rollResults(h.getLevel().random);
            h.assertTrue(output.size() == 1 && output.getFirst().is(CEItems.CARBON_POWDER.get()) && output.getFirst().getCount() == 1, "One fuel makes one carbon powder, without the replaced dye output");
        }
        h.assertTrue(!BuiltInRegistries.ITEM.containsKey(CreatePlant.location("puddled_iron")) && !BuiltInRegistries.ITEM.containsKey(CreatePlant.location("incomplete_puddled_iron")), "Both obsolete puddled-iron items are unregistered");h.succeed();
    }

    @GameTest(template = "test_forging", timeoutTicks = 60)
    public static void nativeBasinRequiresSuperheatingAndPaysFourIronAndOnePowder(GameTestHelper h) {
        var pos = new BlockPos(4, 2, 4);h.setBlock(pos, AllBlocks.BASIN.get());h.setBlock(pos.above(2), AllBlocks.MECHANICAL_MIXER.get());
        h.runAfterDelay(5, () -> {
            var basin = (BasinBlockEntity) h.getBlockEntity(pos);var input = basin.getInputInventory();
            input.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);input.insertItem(1, CEItems.CARBON_POWDER.toStack(), false);
            var recipe = (MixingRecipe) h.getLevel().getRecipeManager().byKey(CreatePlant.location("mixing/unprocessed_steel")).orElseThrow().value();
            h.assertTrue(!BasinRecipe.match(basin, recipe) && !BasinRecipe.apply(basin, recipe), "An unheated basin cannot consume this charge");
            h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));basin.tick();
            h.assertTrue(!BasinRecipe.match(basin, recipe) && !BasinRecipe.apply(basin, recipe) && input.getStackInSlot(0).getCount() == 4, "Ordinary burner heat cannot spend the iron");
            h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING));basin.tick();
            h.assertTrue(BasinRecipe.match(basin, recipe) && input.getStackInSlot(0).getCount() == 4 && input.getStackInSlot(1).getCount() == 1, "Native matching is read-only at superheat");
            h.assertTrue(BasinRecipe.apply(basin, recipe), "Native mixer applies one complete batch");
            int output = 0;for (int slot = 0; slot < basin.getOutputInventory().getSlots(); slot++) if (basin.getOutputInventory().getStackInSlot(slot).is(CEItems.UNPROCESSED_STEEL.get())) output += basin.getOutputInventory().getStackInSlot(slot).getCount();
            h.assertTrue(output == 4 && input.getStackInSlot(0).isEmpty() && input.getStackInSlot(1).isEmpty() && !BasinRecipe.apply(basin, recipe), "Exactly four workpieces are paid once with four iron and one powder");h.succeed();
        });
    }

    @GameTest(template = "test_forging", timeoutTicks = 60)
    public static void pentlanditeAndChromiteBecomeStainlessSteelInTheBasinAlone(GameTestHelper h) {
        var pos = new BlockPos(4, 2, 4);h.setBlock(pos, AllBlocks.BASIN.get());h.setBlock(pos.above(2), AllBlocks.MECHANICAL_MIXER.get());
        h.runAfterDelay(5, () -> {
            var basin = (BasinBlockEntity) h.getBlockEntity(pos);
            // Each step: the recipe, its burner heat, its charge and the one product it must pay.
            record Step(String recipe, BlazeBurnerBlock.HeatLevel heat, ItemStack[] charge, Item product, int count) { }
            Item iron = Items.IRON_INGOT, carbon = CEItems.CARBON_POWDER.get(), pentlandite = item("pentlandite"), oxide = item("nickel_oxide"),
                nickel = item("nickel_ingot"), chromite = item("chromite"), ferrochrome = item("ferrochrome"), stainless = CEItems.STAINLESS_STEEL_INGOT.get();
            var steps = new Step[]{
                new Step("mixing/nickel_oxide_roasted_in_air", BlazeBurnerBlock.HeatLevel.KINDLED, new ItemStack[]{new ItemStack(pentlandite, 2)}, oxide, 1),
                new Step("mixing/nickel_ingot_reduced_with_carbon", BlazeBurnerBlock.HeatLevel.SEETHING, new ItemStack[]{new ItemStack(oxide), new ItemStack(carbon)}, nickel, 1),
                new Step("mixing/ferrochrome", BlazeBurnerBlock.HeatLevel.SEETHING, new ItemStack[]{new ItemStack(chromite, 2), new ItemStack(carbon)}, ferrochrome, 1),
                new Step("mixing/stainless_steel_from_ferrochrome", BlazeBurnerBlock.HeatLevel.SEETHING,
                    new ItemStack[]{new ItemStack(iron, 6), new ItemStack(ferrochrome, 2), new ItemStack(nickel)}, stainless, 10)};
            for (var step : steps) {
                h.setBlock(pos.below(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, step.heat()));basin.tick();
                var input = basin.getInputInventory();var output = basin.getOutputInventory();
                for (int slot = 0; slot < input.getSlots(); slot++) input.extractItem(slot, 64, false);
                for (int slot = 0; slot < output.getSlots(); slot++) output.extractItem(slot, 64, false);
                for (int slot = 0; slot < step.charge().length; slot++) input.insertItem(slot, step.charge()[slot].copy(), false);
                var recipe = (MixingRecipe) h.getLevel().getRecipeManager().byKey(CreatePlant.location(step.recipe())).orElseThrow().value();
                h.assertTrue(BasinRecipe.apply(basin, recipe), "The basin runs " + step.recipe());
                int made = 0;for (int slot = 0; slot < output.getSlots(); slot++) if (output.getStackInSlot(slot).is(step.product())) made += output.getStackInSlot(slot).getCount();
                boolean spent = true;for (int slot = 0; slot < input.getSlots(); slot++) spent &= input.getStackInSlot(slot).isEmpty();
                h.assertTrue(made == step.count() && spent, step.recipe() + " spends its whole charge for " + step.count() + " " + step.product());
            }
            h.succeed();
        });
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(CreatePlant.location(id)); }

    @GameTest(template = "test_forging")
    public static void fourWorkpiecesBecomeFourSteelAfterFiveNativePressPasses(GameTestHelper h) {
        var at = new BlockPos(3, 3, 3);h.setBlock(at, AllBlocks.MECHANICAL_PRESS.get());var press = (MechanicalPressBlockEntity) h.getBlockEntity(at);
        int steel = 0;int batches = press.canProcessInBulk() ? 1 : 4, count = press.canProcessInBulk() ? 4 : 1;
        for (int batch = 0; batch < batches; batch++) {
            var stack = CEItems.UNPROCESSED_STEEL.toStack(count);
            for (int pass = 1; pass <= 5; pass++) {
                var snapshot = stack.copy();var transported = new TransportedItemStack(stack);var outputs = new ArrayList<ItemStack>();
                h.assertTrue(press.tryProcessOnBelt(transported, null, true) && ItemStack.matches(stack, snapshot), "Native pressing simulation cannot advance or shrink the input");
                h.assertTrue(press.tryProcessOnBelt(transported, outputs, false), "Native belt press advances the carbon-steel assembly");
                h.assertTrue(outputs.size() == 1 && outputs.getFirst().getCount() == count, "Every pass conserves the workpiece count");stack = outputs.getFirst();
                if (pass < 5) {
                    var progress = stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);
                    h.assertTrue(stack.is(CEItems.HEATED_UNFINISHED_STEEL.get()) && progress != null && progress.step() == pass && progress.id().equals(CreatePlant.location("sequenced_assembly/steel_ingot")), "Intermediate passes keep their own five-step recipe identity");
                    stack = ItemStack.parseOptional(h.getLevel().registryAccess(), (CompoundTag) stack.save(h.getLevel().registryAccess()));
                } else h.assertTrue(stack.is(CEItems.STEEL_INGOT.get()) && !stack.has(AllDataComponents.SEQUENCED_ASSEMBLY), "Only the fifth pass produces finished steel");
            }
            steel += stack.getCount();
        }
        h.assertTrue(steel == 4, "The four-workpiece mixer batch produces exactly four steel ingots");h.succeed();
    }
}
