package net.createplant.gametest;

import java.util.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.kinetics.deployer.ManualApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEBlocks;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CEItems;
import net.createplant.registry.CEMetals;
import net.createplant.registry.CERecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

/** The reviewed equipment recipes (tools/generate_crafting_recipes.py): no clashing grids, the mechanical crafter for the big machines, and no refined metal in what the refining needs. */
@GameTestHolder(CreatePlant.MOD_ID) @PrefixGameTestTemplate(false)
public class CraftingRecipeGameTests {
    private static final List<String> ADVANCED = List.of("large_flywheel", "high_pressure_turbine_blade",
        "low_pressure_turbine_blade", "cooling_fan", "gas_centrifuge", "cubic_press");
    /** Everything the metal refining needs: stirred tank, reaction trays with the oxidation catalyst, absorption tower, electrolyzer and electrolysis furnace. */
    private static final List<String> REFINING = List.of("reactor_impeller", "vessel_stirrer", "reactor_separator_core", "lidded_vessel", "large_absorption_tower",
        "oxidation_catalyst", "iron_catalyst", "electrolysis_cell", "electrolysis_cell_controller", "electrolysis_electrode_head", "asbestos_wrapped_electrode", "electrolysis_furnace_shell", "electrolysis_furnace_anode", "electrolysis_furnace_busbar",
        "sulfur_dust", "stainless_steel_pipe");

    private static boolean ours(RecipeHolder<?> holder) { return holder.id().getNamespace().equals(CreatePlant.MOD_ID); }
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(CreatePlant.location(id)); }
    private static CraftingInput grid(CraftingRecipe recipe) {
        List<ItemStack> items = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) items.add(ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0]);
        return recipe instanceof ShapedRecipe shaped ? CraftingInput.of(shaped.getWidth(), shaped.getHeight(), items) : CraftingInput.of(3, 3, pad(items));
    }
    private static List<ItemStack> pad(List<ItemStack> items) { while (items.size() < 9) items.add(ItemStack.EMPTY);return items; }
    private static boolean contains(Recipe<?> recipe, Item item) {
        ItemStack stack = new ItemStack(item);
        return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack));
    }

    /** The game leaves out a recipe file it cannot read (a bad field, more inputs than the type takes) with only a log line; every one of ours must load. */
    @GameTest(template = "test_reactor")
    public static void everyRecipeFileOfTheModLoads(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var missing = new ArrayList<String>();
        for (var file : server.getResourceManager().listResources("recipe", path -> path.getNamespace().equals(CreatePlant.MOD_ID) && path.getPath().endsWith(".json")).keySet()) {
            var id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), file.getPath().substring("recipe/".length(), file.getPath().length() - ".json".length()));
            if (server.getRecipeManager().byKey(id).isEmpty()) missing.add(id.toString());
        }
        h.assertTrue(missing.isEmpty(), "Recipe files the game refused to load: " + missing);
        h.succeed();
    }
    /** A tag no installed mod fills loads without error but can never be supplied; every input of ours must name an existing item or fluid. */
    @GameTest(template = "test_reactor")
    public static void everyRecipeInputOfTheModCanBeSupplied(GameTestHelper h) {
        var unsupplied = new ArrayList<String>();
        for (var holder : h.getLevel().getRecipeManager().getRecipes()) {
            if (!ours(holder)) continue;
            for (Ingredient ingredient : holder.value().getIngredients()) if (!ingredient.isEmpty() && ingredient.hasNoItems()) unsupplied.add(holder.id().toString());
            if (holder.value() instanceof ProcessingRecipe<?, ?> processing)
                for (var fluid : processing.getFluidIngredients()) if (fluid.ingredient().hasNoFluids()) unsupplied.add(holder.id().toString());
            if (holder.value() instanceof net.createplant.recipe.ConverterRecipe converter && converter.gasIngredient().ingredient().hasNoFluids()) unsupplied.add(holder.id().toString());
        }
        h.assertTrue(unsupplied.isEmpty(), "Recipe inputs that resolve to nothing: " + unsupplied);
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void obsoleteElectrolysisDefinitionIsGone(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(CreatePlant.location("electrolysis/water")).isEmpty(),
            "The live Basin process has no disconnected legacy water recipe");
        h.assertTrue(!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(CreatePlant.location("electrolysis")),
            "The unused electrolysis recipe type is no longer registered");
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void standaloneReactorDrivesAreGoneButTheVesselAndItsInternalsRemain(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (String name : List.of("reactor_stirred", "reactor_vessel", "reactor_separator", "reactor_centrifuge")) {
            var id = CreatePlant.location(name);
            h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id) && manager.byKey(id).isEmpty(),
                "The retired drive has no registered block, item or recipe: " + id);
        }
        for (String name : List.of("lidded_vessel", "reactor_impeller", "vessel_stirrer", "reactor_separator_core", "large_absorption_tower", "gas_centrifuge", "cubic_press", "tube_piped_tank", "jacketed_pipe"))
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(CreatePlant.location(name)), "The current vessel, internal, tower or heat exchanger remains registered: " + name);
        h.assertTrue(manager.byKey(CreatePlant.location("lidded_vessel")).isPresent() && manager.byKey(CreatePlant.location("stirred_tank")).isPresent()
            && manager.byKey(CreatePlant.location("stirred_tank_from_stainless_tank")).isEmpty(), "Storage and stirred tanks have their own recipes");
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void dioriteCasingUsesCreateHandApplicationAndBothCasingsFitTheMachines(GameTestHelper h) {
        var level = h.getLevel();
        var block = CEBlocks.DIORITE_CASING.get();
        h.assertTrue(block instanceof CasingBlock && block.asItem() == CEItems.DIORITE_CASING.get(),
            "Diorite casing is a real Create casing with its own block item");
        h.assertTrue(block.defaultBlockState().is(net.minecraft.tags.BlockTags.create(ResourceLocation.parse("create:casing")))
            && block.asItem().getDefaultInstance().is(ItemTags.create(ResourceLocation.parse("create:casing"))),
            "Both Create casing tags include diorite casing");
        var manager = level.getRecipeManager();
        var application = manager.byKey(CreatePlant.location("item_application/diorite_casing"));
        h.assertTrue(application.isPresent(), "The diorite hand-application recipe must be loaded");
        var holder = application.orElseThrow();
        h.assertTrue(holder.value() instanceof ManualApplicationRecipe,
            "The survival recipe loads as Create's native manual application recipe");
        var recipe = (ManualApplicationRecipe) holder.value();
        var alloy = com.simibubi.create.AllItems.ANDESITE_ALLOY.get();
        h.assertTrue(recipe.testBlock(Blocks.DIORITE.defaultBlockState())
            && recipe.getRequiredHeldItem().test(alloy.getDefaultInstance()) && !recipe.shouldKeepHeldItem(),
            "One diorite block and one consumed andesite alloy make the casing");
        var deployed = ManualApplicationRecipe.asDeploying(holder).value();
        h.assertTrue(deployed.getResultItem(level.registryAccess()).is(block.asItem()),
            "The same application recipe is available to the standard Deployer");

        var pos = new net.minecraft.core.BlockPos(2, 2, 2);
        h.setBlock(pos, Blocks.DIORITE);
        var absolute = h.absolutePos(pos);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(alloy, 2));
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,
            InteractionHand.MAIN_HAND, absolute, new BlockHitResult(Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled() && level.getBlockState(absolute).is(block)
            && player.getMainHandItem().is(alloy) && player.getMainHandItem().getCount() == 1,
            "Real survival right-click transforms the placed diorite and consumes exactly one alloy");

        var andesite = com.simibubi.create.AllBlocks.ANDESITE_CASING.asStack();
        var diorite = block.asItem().getDefaultInstance();
        for (String name : List.of("pulse_extraction_column", "fusion_console_panel")) {
            var machine = manager.byKey(CreatePlant.location(name)).orElseThrow().value();
            h.assertTrue(machine.getIngredients().stream().anyMatch(ingredient -> ingredient.test(andesite) && ingredient.test(diorite)),
                "The same casing slot accepts either original or white casing: " + name);
        }
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void everyCraftingGridGivesOnlyItsOwnRecipe(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!ours(holder)) continue;
            var matches = manager.getRecipesFor(RecipeType.CRAFTING, grid(holder.value()), h.getLevel());
            h.assertTrue(matches.size() == 1 && matches.get(0).id().equals(holder.id()), holder.id() + " shares its grid with " + matches.stream().map(RecipeHolder::id).toList());
        }
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void theBigMachinesNeedTheMechanicalCrafter(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();var access = h.getLevel().registryAccess();
        Set<Item> crafted = new HashSet<>(), mechanical = new HashSet<>();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) if (ours(holder)) crafted.add(holder.value().getResultItem(access).getItem());
        for (var holder : manager.getAllRecipesFor(AllRecipeTypes.MECHANICAL_CRAFTING.<CraftingInput, CraftingRecipe>getType())) mechanical.add(holder.value().getResultItem(access).getItem());
        for (String id : ADVANCED) h.assertTrue(mechanical.contains(item(id)) && !crafted.contains(item(id)), id + " is made only on the mechanical crafter");
        h.succeed();
    }
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
    @GameTest(template = "test_petrochemical")
    public static void steelCompressionAndEquipmentRecipesStaySeparateFromIndustrialIron(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var access = h.getLevel().registryAccess();
        var packed = manager.byKey(CreatePlant.location("steel_block")).orElseThrow().value();
        var unpacked = manager.byKey(CreatePlant.location("steel_ingot_from_steel_block")).orElseThrow().value();
        h.assertTrue(packed.getIngredients().size() == 9 && packed.getIngredients().stream().allMatch(ingredient -> ingredient.test(CEItems.STEEL_INGOT.toStack()))
            && packed.getResultItem(access).is(CEMetals.STEEL_BLOCK.get()) && packed.getResultItem(access).getCount() == 1
            && contains(unpacked, CEMetals.STEEL_BLOCK.get()) && unpacked.getResultItem(access).is(CEItems.STEEL_INGOT.get())
            && unpacked.getResultItem(access).getCount() == 9, "Nine steel ingots pack into one block and unpack without loss");
        var ingotUses = Set.of("fuel_pool");
        var blockUses = Set.of("high_pressure_turbine_casing",
            "large_flywheel", "reciprocating_compressor", "converter_shell", "converter_trunnion_bearing");
        for (var holder : manager.getRecipes()) {
            if (ours(holder)) h.assertTrue(!contains(holder.value(), AllBlocks.INDUSTRIAL_IRON_BLOCK.asItem()), "Plant recipes must use steel instead of decorative industrial iron: " + holder.id());
            if (ingotUses.contains(holder.id().getPath())) h.assertTrue(contains(holder.value(), CEItems.STEEL_INGOT.get()), "Small parts use steel ingots: " + holder.id());
            if (blockUses.contains(holder.id().getPath())) h.assertTrue(contains(holder.value(), CEMetals.STEEL_BLOCK.get()), "Frames and housings use steel blocks: " + holder.id());
        }
        var magnet = manager.byKey(ResourceLocation.parse("electroenergetics:crafting/magnet_block")).orElseThrow().value();
        h.assertTrue(magnet.getIngredients().stream().filter(ingredient -> ingredient.test(AllBlocks.INDUSTRIAL_IRON_BLOCK.asStack())).count() == 4
            && contains(magnet, AllItems.ANDESITE_ALLOY.get()) && !contains(magnet, CEItems.STEEL_INGOT.get())
            && BuiltInRegistries.ITEM.getKey(magnet.getResultItem(access).getItem()).toString().equals("electroenergetics:magnet")
            && magnet.getResultItem(access).getCount() == 4,
            "The CEE magnet retains its native four-industrial-iron-block recipe and four-magnet output");
        var window = manager.byKey(ResourceLocation.parse("create:industrial_iron_window")).orElseThrow().value();
        var washing = manager.byKey(ResourceLocation.parse("create:splashing/industrial_iron_block")).orElseThrow().value();
        h.assertTrue(contains(window, AllBlocks.INDUSTRIAL_IRON_BLOCK.asItem()) && contains(washing, AllBlocks.INDUSTRIAL_IRON_BLOCK.asItem()),
            "Create's decorative window and washing recipes retain their standard material");
        h.succeed();
    }
    @GameTest(template = "test_petrochemical")
    public static void calciteGivesFluoriteInBothMachinesWithoutCompetingRecipes(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        for (var type : List.of(AllRecipeTypes.CRUSHING.getType(), AllRecipeTypes.MILLING.getType())) {
            var recipes = manager.getRecipes().stream().filter(holder -> holder.value().getType() == type
                && holder.value().getIngredients().size() == 1 && contains(holder.value(), Items.CALCITE)).toList();
            h.assertTrue(recipes.size() == 1, "Each calcite machine has one unambiguous recipe: " + recipes.stream().map(RecipeHolder::id).toList());
            var processing = (com.simibubi.create.content.processing.recipe.StandardProcessingRecipe<?>) recipes.getFirst().value();
            var fluorite = processing.getRollableResults().stream().filter(output -> output.getStack().is(item("fluorite"))).findFirst().orElseThrow();
            var boneMeal = processing.getRollableResults().stream().filter(output -> output.getStack().is(Items.BONE_MEAL)).findFirst().orElseThrow();
            h.assertTrue(fluorite.getStack().getCount() == 1 && Math.abs(fluorite.getChance() - .5f) < 1e-6
                && boneMeal.getStack().getCount() == 1 && Math.abs(boneMeal.getChance() - .75f) < 1e-6
                && processing.getProcessingDuration() == 250, "Calcite gives fluorite at 50 percent and preserves the bone-meal byproduct and processing time");
        }
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void theRefiningEquipmentNeedsOnlyBasinMadeStainlessSteel(GameTestHelper h) {
        Set<Item> refined = new HashSet<>();
        for (String metal : List.of("aluminum", "nickel", "titanium", "zirconium", "chromium", "molybdenum"))
            BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ingots/" + metal))).forEach(holder -> refined.add(holder.value()));
        h.assertTrue(refined.size() == 6, "Six metal ingots are tagged: " + refined.size());
        var access = h.getLevel().registryAccess();Set<Item> checked = new HashSet<>();
        for (var holder : h.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            var result = holder.value().getResultItem(access).getItem();
            // A worn catalyst's regeneration is not how the part is first made.
            if (!ours(holder) || holder.id().getPath().endsWith("_regeneration") || REFINING.stream().noneMatch(id -> item(id) == result)) continue;
            checked.add(result);
            for (Ingredient ingredient : holder.value().getIngredients())
                for (ItemStack stack : ingredient.getItems()) h.assertTrue(!refined.contains(stack.getItem()), holder.id() + " needs " + stack.getItem() + ", which the refining makes");
        }
        h.assertTrue(checked.size() == REFINING.size(), "Every refining part has a crafting recipe: " + checked);
        var plate = (CraftingRecipe) h.getLevel().getRecipeManager().byKey(CreatePlant.location("asbestos_wrapped_electrode")).orElseThrow().value();
        var asbestos = item("asbestos");
        var input = grid(plate);
        h.assertTrue(input.items().stream().filter(stack -> stack.is(asbestos)).count() == 6
            && plate.matches(input, h.getLevel()) && plate.assemble(input, access).getCount() == 4, "Six asbestos make four electrode plates");
        var wool = input.items().stream().map(stack -> stack.is(asbestos) ? new ItemStack(net.minecraft.world.item.Items.GRAY_WOOL) : stack).toList();
        h.assertTrue(!plate.matches(CraftingInput.of(input.width(), input.height(), wool), h.getLevel()), "Gray wool no longer substitutes for asbestos");
        // The stainless steel the machines are made of comes from Create's basin alone.
        var mixing = h.getLevel().getRecipeManager().getAllRecipesFor(com.simibubi.create.AllRecipeTypes.MIXING.getType()).stream().map(holder -> holder.id().getPath()).toList();
        for (String step : List.of("mixing/nickel_oxide_roasted_in_air", "mixing/nickel_ingot_reduced_with_carbon", "mixing/ferrochrome", "mixing/stainless_steel_from_ferrochrome"))
            h.assertTrue(mixing.contains(step), "Basin step before stainless steel: " + step);
        h.succeed();
    }
    @GameTest(template = "test_reactor")
    public static void newMaterialUsesKeepTheRefiningBootstrapOpen(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        Map<String, String> materialUses = Map.of(
            "electrolysis_furnace_shell", "zirconia",
            "flow_regulator", "industrial_resin",
            "fuel_cell", "industrial_resin",
            "relief_scrubber", "industrial_glass",
            "pipe_pressure_gauge", "industrial_glass",
            "pipe_thermometer", "industrial_glass",
            "pipe_flow_meter", "industrial_glass");
        for (var use : materialUses.entrySet()) {
            var holder = manager.byKey(CreatePlant.location(use.getKey()));
            h.assertTrue(holder.isPresent() && holder.orElseThrow().value() instanceof CraftingRecipe,
                "The component recipe exists: " + use.getKey());
            h.assertTrue(contains(holder.orElseThrow().value(), item(use.getValue())),
                use.getKey() + " uses " + use.getValue());
        }
        for (String id : List.of("pressure_reaction/zirconium_sulfate_solution", "pressure_reaction/zirconium_sulfate_evaporation", "pressure_reaction/zirconium_sulfate_dehydration", "pressure_reaction/zirconia")) {
            var holder = manager.byKey(CreatePlant.location(id));
            h.assertTrue(holder.isPresent(), "The zirconia bootstrap recipe exists: " + id);
            h.assertTrue(!contains(holder.orElseThrow().value(), item("electrolysis_furnace_shell")),
                id + " does not consume the refractory-lined furnace");
        }
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void theAbsorptionTowerHasANitricFreeBootstrapRoute(GameTestHelper h) {
        // The tower comes from iron, glass and bars, so sulfuric acid, and with it the aluminium, chromium and stainless steel of
        // later equipment, never waits for nitric acid. Molybdenum for the stainless pipework is made without nitric acid too.
        List<String> bootstrap = List.of(
            "large_absorption_tower", "lidded_vessel", "pressing/stainless_steel_sheet", "mixing/stainless_steel_ingot",
            "pressure_reaction/nickel_oxide", "pressure_reaction/nickel_ingot", "pressure_reaction/chromium_ingot",
            "pressure_reaction/molybdenum_trioxide", "pressure_reaction/molybdenum_ingot",
            "pressure_reaction/sodium_aluminate_solution", "pressure_reaction/alumina_from_sodium_aluminate_solution",
            "pressure_reaction/sulfur_dioxide");
        var manager = h.getLevel().getRecipeManager();
        FluidStack nitricAcid = new FluidStack(CEFluids.NITRIC_ACID.get(), 1);
        for (String id : bootstrap) {
            var holder = manager.byKey(CreatePlant.location(id));
            h.assertTrue(holder.isPresent(), "Nitric-free bootstrap recipe exists: " + id);
            if (holder.orElseThrow().value() instanceof ProcessingRecipe<?, ?> processing)
                for (var ingredient : processing.getFluidIngredients())
                    h.assertTrue(!ingredient.ingredient().test(nitricAcid), id + " must not consume nitric acid");
        }
        for (String process : List.of("oxidize_sulfur_dioxide", "absorb_sulfuric_acid")) {
            var recipe = ProcessRecipe.all().stream().filter(candidate -> candidate.id().equals(process)).findFirst();
            h.assertTrue(recipe.isPresent() && recipe.get().inputs().stream().noneMatch(stack -> stack.is(CEFluids.NITRIC_ACID.get())),
                "Sulfur dioxide reaches sulfuric acid without nitric acid: " + process);
        }
        h.assertTrue(ElectrolysisFurnaceBlockEntity.reductions().containsKey(CEMetals.ALUMINA.get())
            && ElectrolysisFurnaceBlockEntity.reductions().get(CEMetals.ALUMINA.get()).metal() == CEItems.ALUMINUM_INGOT.get(),
            "Alumina reaches the aluminium needed for chromium without nitric acid");
        h.succeed();
    }

    @GameTest(template = "test_reactor")
    public static void bayerSeparationConsumesFiveHundredAndReturnsFourHundred(GameTestHelper h) {
        var manager = h.getLevel().getRecipeManager();
        var sulfate = new FluidStack(CEMetals.ALUMINIUM_SULFATE_SOLUTION.get(), 1);
        var alumina = CEMetals.ALUMINA.get();
        for (var holder : manager.getRecipes()) {
            if (!ours(holder) || !(holder.value() instanceof ProcessingRecipe<?, ?> recipe)) continue;
            h.assertTrue(recipe.getFluidIngredients().stream().noneMatch(input -> input.ingredient().test(sulfate))
                || recipe.getRollableResults().stream().noneMatch(output -> output.getStack().is(alumina)),
                "Aluminium sulfate does not give alumina: " + holder.id());
        }
        var recipe = (PressureReactionRecipe) manager.byKey(CreatePlant.location("pressure_reaction/alumina_from_sodium_aluminate_solution")).orElseThrow().value();
        var aluminate = new FluidStack(CEChlorAlkali.SODIUM_ALUMINATE.get(), 500);
        h.assertTrue(recipe.getFluidIngredients().size() == 1 && recipe.getFluidIngredients().getFirst().test(aluminate)
            && recipe.getFluidIngredients().getFirst().amount() == 500, "One batch consumes 500 mB of sodium aluminate solution");
        h.assertTrue(recipe.getRollableResults().size() == 1 && recipe.getRollableResults().getFirst().getStack().is(alumina)
            && recipe.getRollableResults().getFirst().getStack().getCount() == 1, "One batch produces one alumina");
        h.assertTrue(recipe.getFluidResults().size() == 1 && recipe.getFluidResults().getFirst().is(CEChlorAlkali.CAUSTIC_SODA.get())
            && recipe.getFluidResults().getFirst().getAmount() == 400, "One batch returns 400 mB of caustic soda");
        h.succeed();
    }
}
