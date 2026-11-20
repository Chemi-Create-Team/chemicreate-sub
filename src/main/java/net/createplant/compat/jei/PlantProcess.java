package net.createplant.compat.jei;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * One conversion of a Create-Plant machine as JEI shows it. Each item input lists its accepted
 * alternatives; kept items, such as catalysts, are needed but not consumed. The note names the
 * conditions the amounts depend on.
 */
public record PlantProcess(
        List<List<ItemStack>> items,
        List<FluidStack> fluids,
        List<ItemStack> kept,
        List<ItemStack> results,
        List<FluidStack> fluidResults,
        Component note,
        List<List<FluidStack>> fluidOptions,
        Conditions conditions) {
    public record Conditions(int ticks, HeatCondition heat, int minimumLevels) { }

    public PlantProcess(
            List<List<ItemStack>> items,
            List<FluidStack> fluids,
            List<ItemStack> kept,
            List<ItemStack> results,
            List<FluidStack> fluidResults,
            Component note) {
        this(
                items,
                fluids,
                kept,
                results,
                fluidResults,
                note,
                fluids.stream().map(java.util.List::of).toList(), null);
    }

    public PlantProcess withFluidOptions(List<List<FluidStack>> options) {
        if (options.size() != fluids.size() || options.stream().anyMatch(List::isEmpty))
            throw new IllegalArgumentException("Missing fluid ingredient alternatives");
        return new PlantProcess(items, fluids, kept, results, fluidResults, note, options, conditions);
    }

    public PlantProcess withConditions(int ticks, HeatCondition heat, int minimumLevels) {
        return new PlantProcess(items, fluids, kept, results, fluidResults, note, fluidOptions,
            new Conditions(ticks, heat, minimumLevels));
    }

    public static List<FluidStack> acceptedFluids(FluidStack required) {
        return List.of(required.copy());
    }

    public static List<PlantProcess> ammoniaSynthesis() {
        var recipe=net.createplant.chemistry.ProcessRecipe.all().stream().filter(r->r.id().equals("synthesize_ammonia")).findFirst().orElseThrow();
        return List.of(new PlantProcess(List.of(),recipe.inputs(),List.of(new ItemStack(recipe.catalyst())),List.of(),recipe.outputs(),Component.translatable("jei.create_refinery_n_reactors.note.haber_bosch",Component.translatable("create."+recipe.requiredHeat().getTranslationKey()))));
    }

    public static PlantProcess fluids(List<FluidStack> in, List<FluidStack> out, Component note) {
        return new PlantProcess(List.of(), in, List.of(), List.of(), out, note);
    }
}
