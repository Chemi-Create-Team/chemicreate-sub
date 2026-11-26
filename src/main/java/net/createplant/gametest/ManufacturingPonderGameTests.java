package net.createplant.gametest;

import com.google.gson.Gson;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crusher.CrushingRecipe;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.ponder.ManufacturingRecipe;
import net.createplant.ponder.PlantTutorialCatalog;
import net.createplant.registry.CEItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreatePlant.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManufacturingPonderGameTests {
    private static List<PlantTutorialCatalog.Tutorial> tutorials() {
        return PlantTutorialCatalog.all().stream().filter(tutorial -> tutorial.family() != null).toList();
    }

    @GameTest(template = "test_petrochemical")
    public static void materialsAndEquipmentUseRegisteredItemsAndValidStates(GameTestHelper helper) throws Exception {
        var missing = new ArrayList<String>();
        helper.assertTrue(tutorials().size() == 33, "All manufacturing chapters must ship");
        for (var tutorial : tutorials()) {
            var names = new ArrayList<>(tutorial.components());
            for (var step : tutorial.steps()) {
                if (step.item() != null) names.add(step.item());
                if (step.process().feed() != null) names.add(step.process().feed());
                var process = step.process();
                for (var point : List.of(process.machine(), process.input(), process.output())) {
                    helper.assertTrue(point.length == 3 && point[0] >= 0 && point[0] < process.width()
                        && point[1] >= 0 && point[1] < process.height() && point[2] >= 0 && point[2] < process.width(),
                        "Equipment target lies inside its scene " + tutorial.id());
                }
            }
            for (String name : names) {
                var id = name.contains(":") ? ResourceLocation.parse(name) : CreatePlant.location(name);
                if (BuiltInRegistries.ITEM.get(id) == Items.AIR) missing.add(tutorial.id() + "=" + name);
            }
            try (var stream = PlantTutorialCatalog.class.getResourceAsStream(
                "/assets/" + CreatePlant.MOD_ID + "/ponder/" + tutorial.storyboard() + ".nbt")) {
                helper.assertTrue(stream != null, "Missing manufacturing structure " + tutorial.id());
                PlantTutorialGameTests.validateStructure(helper, stream);
            }
        }
        helper.assertTrue(missing.isEmpty(), "Unknown manufacturing materials " + missing);
        helper.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void chaptersResolveLiveRecipesAndShareFamilyEntrypoints(GameTestHelper helper) {
        var families = new HashMap<String, List<String>>();
        for (var tutorial : tutorials()) {
            var previous = families.putIfAbsent(tutorial.family(), tutorial.components());
            helper.assertTrue(previous == null || previous.equals(tutorial.components()),
                "Every intermediate must open the complete family " + tutorial.family());
            for (var step : tutorial.steps()) {
                var recipe = ManufacturingRecipe.resolve(helper.getLevel(), step.process().recipe());
                if (recipe == null) continue;
                helper.assertTrue(!recipe.items().isEmpty() || !recipe.fluids().isEmpty(), "Recipe has feed " + step.process().recipe());
                helper.assertTrue(!recipe.products().isEmpty() || !recipe.fluidProducts().isEmpty(), "Recipe has products " + step.process().recipe());
            }
        }
        helper.assertTrue(families.size() == 30 && families.containsKey("slime_coolant"),
            "All manufacturing families ship, including the slime coolant route");
        helper.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void everyChapterHasCompleteThreeLanguageRuntimeCaptions(GameTestHelper helper) throws Exception {
        for (String language : List.of("en_us", "ja_jp", "zh_cn")) {
            try (var stream = PlantTutorialCatalog.class.getResourceAsStream(
                "/assets/" + CreatePlant.MOD_ID + "/lang/" + language + ".json")) {
                @SuppressWarnings("unchecked")
                Map<String, String> strings = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Map.class);
                for (var tutorial : tutorials()) {
                    String prefix = CreatePlant.MOD_ID + ".ponder.shared.guide." + tutorial.id() + ".";
                    String header = CreatePlant.MOD_ID + ".ponder.plant_" + tutorial.id() + ".header";
                    helper.assertTrue(strings.containsKey(prefix + "title") && strings.get(prefix + "title").equals(strings.get(header)),
                        "Native Ponder header is translated " + language + " " + tutorial.id());
                    for (var step : tutorial.steps()) {
                        String text = strings.get(prefix + step.key());
                        helper.assertTrue(text != null && !text.isBlank(), "Missing caption " + language + " " + tutorial.id());
                        helper.assertTrue(step.process().recipe() == null ? !text.contains("%") : text.endsWith("%s → %s")
                            && text.substring(0, text.length() - "%s → %s".length()).indexOf('%') < 0,
                            "Live recipe caption has exactly two formatting arguments " + language + " " + tutorial.id());
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void quantitiesChancesAndAssemblyRepetitionsMatchTheLiveRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var steel = ManufacturingRecipe.resolve(level, CreatePlant.MOD_ID + ":sequenced_assembly/steel_ingot");
        helper.assertTrue(steel.cycles() == 5 && !steel.intermediate().isEmpty(), "The same steel workpiece receives five presses");
        var recipes = level.getRecipeManager().getRecipes().stream().filter(holder -> holder.value().getType() == AllRecipeTypes.CRUSHING.getType()
            && holder.value().getIngredients().getFirst().test(Items.NETHERRACK.getDefaultInstance())).toList();
        helper.assertTrue(recipes.size() == 1, "Netherrack has exactly one matching crushing recipe");
        var selected = (CrushingRecipe) recipes.getFirst().value();
        var lesson = ManufacturingRecipe.resolve(level, "create:crushing/netherrack");
        helper.assertTrue(lesson.products().getFirst().is(CEItems.SULFUR_DUST.get()) && lesson.chances().getFirst() == .3f,
            "Ponder shows the actual 30% sulfur yield");
        var outputs = selected.getRollableResults();
        if (recipes.getFirst().id().getPath().equals("crushing/netherrack")) {
            helper.assertTrue(selected.getProcessingDuration() == 250 && outputs.size() == 3
                && outputs.get(1).getStack().is(AllItems.CINDER_FLOUR.get()) && outputs.get(1).getStack().getCount() == 1
                && outputs.get(1).getChance() == 1 && outputs.get(2).getStack().is(AllItems.CINDER_FLOUR.get())
                && outputs.get(2).getStack().getCount() == 1 && outputs.get(2).getChance() == .5f,
                "The combined recipe preserves Create's processing time and both cinder flour rolls");
        } else {
            helper.assertTrue(selected.getProcessingDuration() == 350 && outputs.size() == 2
                && BuiltInRegistries.ITEM.getKey(outputs.get(1).getStack().getItem()).toString().equals("exnihilosequentia:crushed_netherrack"),
                "The compatibility recipe preserves crushed netherrack");
        }
        var alloy = ManufacturingRecipe.resolve(level, CreatePlant.MOD_ID + ":mixing/stainless_steel_ingot");
        helper.assertTrue(alloy.items().stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum() == 10
            && alloy.fluidProducts().getFirst().getAmount() == 600, "Repeated ingredients retain alloy batch quantities");
        var converter = ManufacturingRecipe.resolve(level, CreatePlant.MOD_ID + ":converter/refine_hot_metal");
        var actual = (net.createplant.recipe.ConverterRecipe) level.getRecipeManager().byKey(
            CreatePlant.location("converter/refine_hot_metal")).orElseThrow().value();
        helper.assertTrue(converter.items().getFirst().getCount() == actual.itemIngredients().getFirst().count(),
            "Bulk converter charges retain sized ingredient quantities");
        helper.succeed();
    }

    @GameTest(template = "test_petrochemical")
    public static void fractionationLessonsUseMinimumHeightAndRealPorts(GameTestHelper helper) throws Exception {
        for (var tutorial : tutorials()) {
            try (var stream = PlantTutorialCatalog.class.getResourceAsStream(
                "/assets/" + CreatePlant.MOD_ID + "/ponder/" + tutorial.storyboard() + ".nbt")) {
                var structure = PlantTutorialGameTests.validateStructure(helper, stream);
                var palette = structure.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
                var blocks = new HashMap<net.minecraft.core.BlockPos, net.minecraft.nbt.CompoundTag>();
                for (var value : structure.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    var block = (net.minecraft.nbt.CompoundTag) value;
                    var position = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
                    blocks.put(new net.minecraft.core.BlockPos(position.getInt(0), position.getInt(1), position.getInt(2)),
                        palette.getCompound(block.getInt("state")));
                }
                for (var step : tutorial.steps()) {
                    var process = step.process();
                    if (!process.kind().equals("column")) continue;
                    boolean air = process.recipe().equals("operation:air_separation");
                    String id = air ? "air" : process.recipe().substring("process:".length());
                    int height = net.createplant.chemistry.DistillationProfile.requiredHeight(id);
                    helper.assertTrue(process.height() == height + 2, "Minimum column height: " + id);
                    var origin = new net.minecraft.core.BlockPos(process.offset() + 5, 1, 5);
                    long cells = blocks.entrySet().stream().filter(entry -> entry.getValue().getString("Name").equals(CreatePlant.MOD_ID + ":fractionating_column")
                        && entry.getKey().getX() >= process.offset() && entry.getKey().getX() < process.offset() + process.width()).count();
                    helper.assertTrue(cells == 4L * height, "Every column layer is exactly 2×2: " + id);
                    int inlet = net.createplant.chemistry.DistillationProfile.feedLayer(height);
                    helper.assertTrue(blocks.get(origin.east().above(inlet)).getCompound("Properties").getString("inlet").equals("1"), "Feed uses the real east port: " + id);
                    int cuts = air ? 3 : net.createplant.chemistry.DistillationProfile.fraction(id).outputs().size();
                    for (int slot = 1; slot <= cuts; slot++) {
                        int layer = air ? new int[]{0, 9, 13}[slot - 1]
                            : net.createplant.chemistry.DistillationProfile.fractionLayer(slot, height, cuts);
                        helper.assertTrue(blocks.get(origin.above(layer).north()).getString("Name").equals("create:smart_fluid_pipe"), "Each cut has a separate north pipe: " + id);
                    }
                }
            }
        }
        helper.succeed();
    }

}
