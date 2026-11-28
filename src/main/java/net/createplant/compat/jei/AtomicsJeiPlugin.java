package net.createplant.compat.jei;

import net.createplant.registry.CEAtomicsRecipeTypes;
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
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.factory.FactoryKind;
import net.createplant.recipe.ContinuousFactoryRecipe;
import net.createplant.recipe.ConverterRecipe;
import net.createplant.recipe.LargePressingRecipe;
import net.createplant.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.createplant.compat.jei.CreatePlantJeiPlugin.*;
import net.createplant.compat.jei.CreatePlantJeiPlugin.Kind;

@JeiPlugin
public class AtomicsJeiPlugin implements IModPlugin {
    private static List<Kind> kinds;

    private static List<Kind> kinds() {
        if (kinds == null) kinds = List.of(
            new Kind("irradiation_products", "nuclear/rbmk_core", CENuclear.FUEL_ROD.get(), CENuclear.FUEL_ROD.get()),
            new Kind("fusion_service", "nuclear/fusion_tokamak", CEFusion.IRRADIATION_CARTRIDGE.get(), CEFusion.IRRADIATION_CARTRIDGE.get()),
            new Kind("cubic_pressing", "plant/cubic_press", CEHighStressMachines.PRESS.get(), CEHighStressMachines.PRESS.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("large_pressing", "plant/large_press", CELargePress.PRESS.get(), CELargePress.PRESS.get(), CELargePress.DEPOT.get()),
            new Kind("rotary_kiln", "plant/rotary_kiln", CEContinuousFactory.KILN.get(), CEContinuousFactory.KILN.get(), AllBlocks.BLAZE_BURNER.get()),
            new Kind("rolling_mill", "plant/rolling_mill", CEContinuousFactory.MILL.get(), CEContinuousFactory.MILL.get(), CEContinuousFactory.STAND.get()),
            new Kind("coil_winder", "plant/coil_winder", CEContinuousFactory.WINDER.get(), CEContinuousFactory.WINDER.get(), CEContinuousFactory.BRAKE.get()),
            new Kind("gas_centrifuge", "plant/gas_centrifuge", CEHighStressMachines.CENTRIFUGE.get(), CEHighStressMachines.CENTRIFUGE.get()),
            new Kind("converter", "plant/converter", CEConverter.SHELL.get(), CEConverter.SHELL.get()),
            new Kind("solvent_extraction", "plant/fuel_cycle", CEFuelCycle.EXTRACTION_COLUMN.get(), CEFuelCycle.EXTRACTION_COLUMN.get()),
            new Kind("vitrification", "plant/fuel_cycle", CEFuelCycle.MELTER.get(), CEFuelCycle.MELTER.get()));
        return kinds;
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) { addCategories(registration, kinds()); }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) { addCatalysts(registration, kinds()); }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var world = Minecraft.getInstance().level;
        if (world != null) for (var kind : FactoryKind.values()) {
            registration.addRecipes(type(kind.id), world.getRecipeManager()
                .getAllRecipesFor(CEAtomicsRecipeTypes.CONTINUOUS_PROCESSING.<RecipeInput, ContinuousFactoryRecipe>getType()).stream()
                .filter(holder -> holder.value().machine() == kind)
                .sorted(java.util.Comparator.comparing((RecipeHolder<ContinuousFactoryRecipe> holder) -> holder.value().minimumDamage() > 0)
                    .thenComparing(holder -> holder.id().getPath()))
                .map(holder -> continuous(holder.value())).toList());
        }
        if (world != null) registration.addRecipes(type("large_pressing"), world.getRecipeManager()
            .getAllRecipesFor(CEAtomicsRecipeTypes.LARGE_PRESSING.<SingleRecipeInput, LargePressingRecipe>getType()).stream().map(holder -> {
                var recipe = holder.value();
                return new PlantProcess(List.of(Arrays.asList(recipe.ingredient().getItems())), List.of(), List.of(),
                    List.of(recipe.result().copy()), List.of(), note("large_pressing"));
            }).toList());
        registration.addRecipes(type("irradiation_products"), List.of(
            new PlantProcess(List.of(List.of(stack("fuel_rod"), stack("compact_enriched_fuel_rod"), stack("mox_fuel_rod"))), List.of(), List.of(), List.of(stack("spent_fuel_rod")), List.of(), note("spent_fuel")),
            service("control_rod", "spent_control_rod", "spent_control"),
            service("lithium_target_rod", "irradiated_lithium_target_rod", "irradiated_target")));
        registration.addRecipes(type("fusion_service"), List.of(service("irradiation_cartridge", "irradiated_cartridge", "irradiated_cartridge"), service("tungsten_divertor_cassette", "spent_divertor_cassette", "spent_divertor")));
        if (Minecraft.getInstance().level != null) registration.addRecipes(type("cubic_pressing"), Minecraft.getInstance().level.getRecipeManager()
            .getAllRecipesFor(CEAtomicsRecipeTypes.CUBIC_PRESSING.<net.createplant.recipe.CubicPressRecipe.Input, net.createplant.recipe.CubicPressRecipe>getType()).stream().map(holder -> {
                var recipe = holder.value();
                return new PlantProcess(recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList(), List.of(), List.of(),
                    recipe.getRollableResultsAsItemStacks(), List.of(), note("cubic_pressing", recipe.getProcessingDuration(), Math.round(recipe.getRollableResults().size() > 1 ? recipe.getRollableResults().get(1).getChance() * 100 : 0)));
            }).toList());
        registration.addRecipes(type("converter"), ordinaryRoutesFirst(converterReactions()));
        registration.addRecipes(type("solvent_extraction"), ordinaryRoutesFirst(fuelCycleReactions(CEAtomicsRecipeTypes.SOLVENT_EXTRACTION)));
        registration.addRecipes(type("vitrification"), ordinaryRoutesFirst(fuelCycleReactions(CEAtomicsRecipeTypes.VITRIFICATION)));
        List<PlantProcess> centrifuge = new ArrayList<>();
        for (ProcessRecipe recipe : ProcessRecipe.all()) if (recipe.module() == ProcessModule.GAS_CENTRIFUGE && !recipe.id().equals("wash_raw_process_gas")) {
            var inputs = recipe.inputs();
            Component conditions = note("centrifuge_stages", recipe.ticks(), net.createplant.block.entity.GasCentrifugeBlockEntity.requiredStages(recipe.id()));
            if (recipe.requiredHeat() != com.simibubi.create.content.processing.recipe.HeatCondition.NONE) conditions = conditions.copy().append(" · ").append(Component.translatable("create." + recipe.requiredHeat().getTranslationKey()));
            if (inputs.stream().anyMatch(input -> PlantProcess.acceptedFluids(input).size() > 1)) conditions = note("cooled_feed").copy().append(" ").append(conditions);
            centrifuge.add(new PlantProcess(List.of(), inputs, recipe.catalyst() == Items.AIR ? List.of() : List.of(new ItemStack(recipe.catalyst())),
                List.of(), recipe.outputs(), conditions).withFluidOptions(inputs.stream().map(PlantProcess::acceptedFluids).toList()));
        }
        registration.addRecipes(type("gas_centrifuge"), ordinaryRoutesFirst(centrifuge));
    }

    private static PlantProcess continuous(ContinuousFactoryRecipe recipe) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        inputs.add(Arrays.stream(recipe.feed().ingredient().getItems()).map(stack -> {
            var feed = stack.copyWithCount(recipe.feed().count());
            if (recipe.minimumDamage() != 0) feed.setDamageValue(recipe.minimumDamage());
            return feed;
        }).toList());
        recipe.auxiliary().ifPresent(aux -> inputs.add(Arrays.stream(aux.ingredient().getItems()).map(stack -> stack.copyWithCount(aux.count())).toList()));
        var text = note(recipe.machine().id + (recipe.cold() ? "_cold" : recipe.minimumDamage() > 0 ? "_worn" : recipe.heat() == 2 ? "_strong" : "")).copy();
        if (recipe.minStands() > 1 || recipe.maxStands() < 8)
            text.append(" ").append(note("stands", recipe.minStands() > 1 ? recipe.minStands() : 2, recipe.maxStands() < 8 ? recipe.maxStands() : 5));
        if (recipe.rpmTolerance() != .10f) text.append(" ").append(note("rpm_tolerance", Math.round(recipe.rpmTolerance() * 100)));
        return new PlantProcess(inputs, List.of(), List.of(), List.of(recipe.result().copy()), List.of(), text);
    }

    private static List<PlantProcess> fuelCycleReactions(com.simibubi.create.foundation.recipe.IRecipeTypeInfo type) {
        var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(type.<net.minecraft.world.item.crafting.RecipeInput, net.createplant.recipe.FuelCycleRecipe>getType()).stream().map(holder -> {
            var recipe = holder.value();
            return new PlantProcess(recipe.getIngredients().stream().map(ingredient -> Arrays.asList(ingredient.getItems())).toList(),
                recipe.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList(), List.of(),
                recipe.getRollableResults().stream().map(ProcessingOutput::getStack).toList(), recipe.getFluidResults(), note(type.getId().getPath(), recipe.getProcessingDuration())).withFluidOptions(recipe.getFluidIngredients().stream().map(ingredient->Arrays.asList(ingredient.getFluids())).toList());
        }).toList();
    }

    /** Converter recipes retain their large ingredient counts instead of expanding them into crafting-grid duplicates. */
    private static List<PlantProcess> converterReactions() {
        List<PlantProcess> list = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return list;
        for (RecipeHolder<ConverterRecipe> holder : level.getRecipeManager().getAllRecipesFor(
            CEAtomicsRecipeTypes.CONVERTER.<net.minecraft.world.item.crafting.RecipeInput, ConverterRecipe>getType())) {
            ConverterRecipe recipe = holder.value();
            if (recipe.legacy()) continue;
            List<List<ItemStack>> items = recipe.itemIngredients().stream()
                .map(ingredient -> Arrays.asList(ingredient.getItems())).toList();
            List<List<FluidStack>> options = new ArrayList<>();
            options.add(Arrays.asList(recipe.gasIngredient().getFluids()));
            recipe.metalIngredient().ifPresent(ingredient -> options.add(Arrays.asList(ingredient.getFluids())));
            List<FluidStack> fluids = options.stream().map(List::getFirst).toList();
            List<FluidStack> fluidResults = java.util.stream.Stream.of(recipe.metalResult(), recipe.byproduct())
                .filter(fluid -> !fluid.isEmpty()).toList();
            list.add(new PlantProcess(items, fluids, List.of(), List.of(recipe.result()), fluidResults,
                note("converter", recipe.duration())).withFluidOptions(options));
        }
        return list;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return CreatePlant.location("atomics_jei_plugin");
    }
}
