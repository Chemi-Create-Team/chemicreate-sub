package net.createplant.ponder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.createplant.CreatePlant;
import net.createplant.chemistry.ElectrolysisProcess;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.chemistry.ProcessModule;
import net.createplant.chemistry.ProcessRecipe;
import net.createplant.chemistry.ReactorModule;
import net.createplant.recipe.ConverterRecipe;
import net.createplant.recipe.PressureReactionRecipe;
import net.createplant.registry.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Ingredient snapshots for Ponder come from the same recipes that the machines execute. */
public record ManufacturingRecipe(List<ItemStack> items, List<FluidStack> fluids, List<ItemStack> products,
    List<FluidStack> fluidProducts, ItemStack catalyst, ReactorModule module, HeatCondition heat,
    List<Float> chances, int cycles, ItemStack intermediate) {
    private static final ResourceLocation NETHERRACK_CRUSHING = ResourceLocation.fromNamespaceAndPath("create", "crushing/netherrack");
    private static final ResourceLocation EX_NIHILO_NETHERRACK_CRUSHING = ResourceLocation.fromNamespaceAndPath("create", "crushing/compat/exnihilosequentia/netherrack");

    private ManufacturingRecipe(List<ItemStack> items, List<FluidStack> fluids, List<ItemStack> products,
        List<FluidStack> fluidProducts, ItemStack catalyst, ReactorModule module, HeatCondition heat) {
        this(items, fluids, products, fluidProducts, catalyst, module, heat, List.of(), 1, ItemStack.EMPTY);
    }

    public static ManufacturingRecipe resolve(Level level, String name) {
        if (name == null) return null;
        var id = ResourceLocation.parse(name);
        if (id.getNamespace().equals("process")) {
            var recipe = ProcessRecipe.all().stream().filter(candidate -> candidate.id().equals(id.getPath())).findFirst().orElseThrow();
            var module = Arrays.stream(ReactorModule.values()).filter(candidate -> candidate.process == recipe.module()).findFirst().orElse(ReactorModule.NONE);
            var outputs = recipe.module() == ProcessModule.FRACTION_SEPARATOR ? DistillationProfile.fraction(recipe.id()).outputs() : recipe.outputs();
            return new ManufacturingRecipe(List.of(), recipe.inputs(), List.of(), outputs,
                recipe.catalyst() == Items.AIR ? ItemStack.EMPTY : new ItemStack(recipe.catalyst()), module, recipe.requiredHeat());
        }
        if (id.getNamespace().equals("operation")) return operation(id.getPath());
        var manager = level.getRecipeManager();
        var holder = manager.byKey(id);
        if (holder.isEmpty() && id.equals(NETHERRACK_CRUSHING)) holder = manager.byKey(EX_NIHILO_NETHERRACK_CRUSHING);
        var recipe = holder.orElseThrow(() -> new IllegalArgumentException("Missing manufacturing recipe " + id)).value();
        if (recipe instanceof ConverterRecipe converter) {
            return new ManufacturingRecipe(converter.itemIngredients().stream().map(ingredient -> ingredient.getItems()[0].copy()).toList(),
                List.of(converter.gasIngredient().getFluids()[0]), List.of(converter.result()), converter.byproduct().isEmpty() ? List.of() : List.of(converter.byproduct()),
                ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        if (recipe instanceof SequencedAssemblyRecipe assembly) {
            return new ManufacturingRecipe(List.of(assembly.getIngredient().getItems()[0].copy()), List.of(),
                assembly.resultPool.stream().map(output -> output.getStack().copy()).toList(), List.of(), ItemStack.EMPTY,
                ReactorModule.NONE, HeatCondition.NONE, assembly.resultPool.stream().map(output -> output.getChance()).toList(),
                assembly.getLoops(), assembly.getTransitionalItem().copy());
        }
        if (recipe instanceof StandardProcessingRecipe<?> processing) {
            return new ManufacturingRecipe(merge(processing.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).map(ingredient -> ingredient.getItems()[0]).toList()),
                processing.getFluidIngredients().stream().map(ingredient -> ingredient.getFluids()[0]).toList(),
                processing.getRollableResults().stream().map(output -> output.getStack().copy()).toList(), processing.getFluidResults(), ItemStack.EMPTY,
                recipe instanceof PressureReactionRecipe pressure ? pressure.module() : ReactorModule.STIRRED, processing.getRequiredHeat(),
                processing.getRollableResults().stream().map(output -> output.getChance()).toList(), 1, ItemStack.EMPTY);
        }
        return new ManufacturingRecipe(merge(recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).map(ingredient -> ingredient.getItems()[0]).toList()),
            List.of(), List.of(recipe.getResultItem(level.registryAccess())), List.of(), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
    }

    private static ManufacturingRecipe operation(String name) {
        if (name.equals("cool_ammonia") || name.equals("condense_high_pressure_steam") || name.startsWith("exchange_")) {
            var source = new FluidStack(name.startsWith("exchange_olefins_") ? CEProcessFluids.HOT_OLEFINS.get()
                : name.equals("exchange_chlorine_ammonia") ? CEChlorAlkali.CHLORINE.get() : name.equals("condense_high_pressure_steam") ? CEProcessFluids.HIGH_PRESSURE_STEAM.get() : CEFluids.AMMONIA_GAS.get(), 1);
            var conversion = net.createplant.thermal.ThermalStreams.cooling(source);
            source.setAmount(conversion.inputAmount());
            if (name.equals("cool_ammonia") || name.equals("condense_high_pressure_steam")) return new ManufacturingRecipe(List.of(), List.of(source), List.of(),
                List.of(conversion.result(source, 1)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
            var coolant = new FluidStack(name.endsWith("_oxygen") ? CEFluids.LIQUID_OXYGEN.get() : CEProcessFluids.LIQUID_AMMONIA.get(), 1);
            var evaporation = net.createplant.thermal.ThermalStreams.coolant(coolant);
            return new ManufacturingRecipe(List.of(), List.of(source, coolant), List.of(),
                List.of(conversion.result(source, 1), evaporation.result(coolant, 1)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        if (name.equals("liquefy_nitrogen")) {
            var source = new FluidStack(CEFluids.NITROGEN_GAS.get(), 1);
            var conversion = net.createplant.thermal.ThermalStreams.liquefaction(source);source.setAmount(conversion.inputAmount());
            return new ManufacturingRecipe(List.of(), List.of(source, new FluidStack(Fluids.WATER, 50)), List.of(),
                List.of(conversion.result(source, 1), new FluidStack(CEProcessFluids.WARM_WATER.get(), 50)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        if (name.equals("tube_steam")) return new ManufacturingRecipe(List.of(), List.of(new FluidStack(Fluids.WATER, 1000)),
            List.of(), List.of(new FluidStack(CEProcessFluids.HIGH_PRESSURE_STEAM.get(), 1000)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.HEATED);
        if (name.equals("haber_bosch")) {
            var display=net.createplant.compat.jei.PlantProcess.ammoniaSynthesis().getFirst();
            return new ManufacturingRecipe(List.of(),display.fluids(),List.of(),display.fluidResults(),
                display.kept().getFirst(),ReactorModule.NONE,HeatCondition.HEATED);
        }
        if (name.startsWith("reduce_")) {
            var oxide = new ItemStack(BuiltInRegistries.ITEM.get(CreatePlant.location(name.substring(7))));
            var reduction = ElectrolysisFurnaceBlockEntity.reductions().get(oxide.getItem());
            if (reduction == null) throw new IllegalArgumentException("Unknown electrolysis feed " + name);
            return new ManufacturingRecipe(List.of(oxide, new ItemStack(Items.COAL)), List.of(), List.of(new ItemStack(reduction.metal())), List.of(),
                ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        if (name.equals("air_separation")) {
            return new ManufacturingRecipe(List.of(), List.of(new FluidStack(CEProcessFluids.COMPRESSED_AIR.get(), DistillationProfile.AIR_BATCH)), List.of(),
                List.of(new FluidStack(CEFluids.LIQUID_OXYGEN.get(), DistillationProfile.PRODUCT_AMOUNT[0]),
                    new FluidStack(CEFluids.ARGON_GAS.get(), DistillationProfile.PRODUCT_AMOUNT[1]), new FluidStack(CEFluids.NITROGEN_GAS.get(), DistillationProfile.PRODUCT_AMOUNT[2])),
                ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        var feed = switch (name) {
            case "water_electrolysis" -> Fluids.WATER;
            case "heavy_water_electrolysis" -> CEFusionFuel.HEAVY_WATER.get();
            case "brine_electrolysis" -> CEChlorAlkali.BRINE.get();
            case "fluoride_electrolysis" -> CEUranium.HYDROGEN_FLUORIDE.get();
            case "sulfurous_electrolysis" -> CEUranium.SULFUROUS_ACID.get();
            default -> throw new IllegalArgumentException("Unknown manufacturing operation " + name);
        };
        var process = ElectrolysisProcess.of(new FluidStack(feed, 1));
        int ticks = 100;
        var outputs = new ArrayList<>(List.of(new FluidStack(process.cathode(), process.cathodeAmount() * ticks)));
        if (process.anodeGas()) outputs.add(new FluidStack(process.anode(), ticks));
        if (process.recoveredAmount() > 0) outputs.add(new FluidStack(process.recovered(), process.recoveredAmount() * ticks));
        if (process.soda() > 0) outputs.add(new FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), process.soda() * ticks));
        return new ManufacturingRecipe(List.of(), List.of(new FluidStack(feed, process.input() * ticks)), List.of(), outputs,
            ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
    }

    public String inputDescription() { return describe(items, fluids, List.of()); }
    public String outputDescription() { return describe(products, fluidProducts, chances); }

    private static String describe(List<ItemStack> items, List<FluidStack> fluids, List<Float> chances) {
        var parts = new ArrayList<String>();
        for (int index = 0; index < items.size(); index++) {
            var item = items.get(index);
            String chance = index < chances.size() && chances.get(index) < 1
                ? " (" + Math.round(chances.get(index) * 100) + "%)" : "";
            parts.add(item.getCount() + " × " + item.getHoverName().getString() + chance);
        }
        for (var fluid : fluids) parts.add(fluid.getAmount() + " mB " + fluid.getHoverName().getString());
        return String.join(" + ", parts);
    }

    private static List<ItemStack> merge(List<ItemStack> stacks) {
        var merged = new ArrayList<ItemStack>();
        for (var stack : stacks) {
            var previous = merged.stream().filter(candidate -> ItemStack.isSameItemSameComponents(candidate, stack)).findFirst().orElse(null);
            if (previous == null) merged.add(stack.copy()); else previous.grow(stack.getCount());
        }
        return merged;
    }
}
