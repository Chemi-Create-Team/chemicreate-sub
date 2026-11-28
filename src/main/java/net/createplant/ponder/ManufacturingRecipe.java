package net.createplant.ponder;

import net.createplant.registry.CEIsotopeGases;
import net.createplant.registry.CESharedChemicals;
import net.createplant.thermal.FluidForm;
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
            var feeds = new ArrayList<FluidStack>();
            feeds.add(converter.gasIngredient().getFluids()[0]);
            converter.metalIngredient().ifPresent(metal -> feeds.add(metal.getFluids()[0]));
            var products = java.util.stream.Stream.of(converter.metalResult(), converter.byproduct())
                .filter(fluid -> !fluid.isEmpty()).toList();
            return new ManufacturingRecipe(converter.itemIngredients().stream().map(ingredient -> ingredient.getItems()[0].copy()).toList(),
                feeds, List.of(converter.result()), products,
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
            var source = name.startsWith("exchange_olefins_") ? FluidForm.HOT_OLEFINS.stack(1)
                : name.equals("exchange_chlorine_ammonia") ? new FluidStack(CEChlorAlkali.CHLORINE.get(), 1) : name.equals("condense_high_pressure_steam") ? FluidForm.HIGH_PRESSURE_STEAM.stack(1) : new FluidStack(CEFluids.AMMONIA_GAS.get(), 1);
            if(name.equals("cool_ammonia") || name.equals("condense_high_pressure_steam")) {
                boolean high=name.equals("condense_high_pressure_steam");
                source=net.createplant.thermal.FluidState.with(new FluidStack(Fluids.WATER,1000),high ? 12 : 10,high ? 12 : 4);
                var step=net.createplant.thermal.PhaseChange.transfer(source,net.createplant.block.entity.RadiatorBlockEntity.cooling(source,net.createplant.thermal.FluidState.kelvin(high ? 12 : 10)).level(),high ? 12 : 4);
                return new ManufacturingRecipe(List.of(),List.of(source),List.of(),List.of(step.output(step.batches(1000))),
                    ItemStack.EMPTY,ReactorModule.NONE,HeatCondition.NONE);
            }
            var coolant=name.endsWith("_oxygen") ? FluidForm.LIQUID_OXYGEN.stack(1) : FluidForm.LIQUID_AMMONIA.stack(1);
            if(name.equals("exchange_chlorine_ammonia"))coolant=net.createplant.thermal.FluidState.with(coolant,1,4);
            int a=net.createplant.thermal.FluidState.temperature(source), b=net.createplant.thermal.FluidState.temperature(coolant);
            if(!name.startsWith("exchange_olefins")) {
                source=net.createplant.thermal.FluidState.with(source,6,4);
                a=6;
            }
            var exchange=net.createplant.thermal.ThermalEquipment.exchange(source,net.createplant.thermal.FluidState.kelvin(a),coolant,net.createplant.thermal.FluidState.kelvin(b),false);
            var hot=net.createplant.thermal.PhaseChange.transfer(source,exchange.firstLevel(),net.createplant.thermal.FluidState.pressure(source));
            var cold=net.createplant.thermal.PhaseChange.transfer(coolant,exchange.secondLevel(),net.createplant.thermal.FluidState.pressure(coolant));
            int amount=hot.consumed()*cold.consumed();
            return new ManufacturingRecipe(List.of(),List.of(source.copyWithAmount(amount),coolant.copyWithAmount(amount)),List.of(),
                List.of(hot.output(amount/hot.consumed()),cold.output(amount/cold.consumed())), ItemStack.EMPTY,ReactorModule.NONE,HeatCondition.NONE);
        }
        if (name.equals("liquefy_nitrogen")) {
            var source = new FluidStack(CEFluids.NITROGEN_GAS.get(), 1);
            var conversion = net.createplant.thermal.ThermalStreams.liquefaction(source);source.setAmount(conversion.inputAmount());
            return new ManufacturingRecipe(List.of(), List.of(source, new FluidStack(Fluids.WATER, 50)), List.of(),
                List.of(conversion.result(source, 1), FluidForm.WARM_WATER.stack(50)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        if (name.equals("tube_steam")) return new ManufacturingRecipe(List.of(), List.of(new FluidStack(Fluids.WATER, 1000)),
            List.of(), List.of(FluidForm.HIGH_PRESSURE_STEAM.stack(1000)), ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.HEATED);
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
            return new ManufacturingRecipe(List.of(), List.of(FluidForm.COMPRESSED_AIR.stack(DistillationProfile.AIR_BATCH)), List.of(),
                List.of(FluidForm.LIQUID_OXYGEN.stack(DistillationProfile.PRODUCT_AMOUNT[0]),
                    new FluidStack(CEFluids.ARGON_GAS.get(), DistillationProfile.PRODUCT_AMOUNT[1]), new FluidStack(CEFluids.NITROGEN_GAS.get(), DistillationProfile.PRODUCT_AMOUNT[2])),
                ItemStack.EMPTY, ReactorModule.NONE, HeatCondition.NONE);
        }
        var feed = switch (name) {
            case "water_electrolysis" -> Fluids.WATER;
            case "heavy_water_electrolysis" -> CEIsotopeGases.HEAVY_WATER.get();
            case "brine_electrolysis" -> CEChlorAlkali.BRINE.get();
            case "fluoride_electrolysis" -> CESharedChemicals.HYDROGEN_FLUORIDE.get();
            case "sulfurous_electrolysis" -> CESharedChemicals.SULFUROUS_ACID.get();
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
