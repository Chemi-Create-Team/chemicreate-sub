package net.createplant.mixture;

import java.util.*;
import net.createplant.chemistry.ChemicalReadout;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/** Full composition for displays, and a bounded four-line summary for goggles. */
public final class MixtureGoggleHelper {
    private MixtureGoggleHelper() { }
    public static List<Component> describe(FluidStack stack) {
        if (stack.isEmpty()) return List.of();
        int total = stack.getAmount();var lines = new ArrayList<Component>();
        lines.add(Component.translatable("mixture.create_refinery_n_reactors.total", total));
        var sorted = new ArrayList<>(MixtureHelper.components(stack));
        sorted.sort(Comparator.comparingInt(FluidStack::getAmount).reversed().thenComparing(part -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(part.getFluid())));
        for (var component : sorted) lines.add(Component.translatable("mixture.create_refinery_n_reactors.component", component.getHoverName(), component.getAmount(), String.format(Locale.ROOT, "%.1f", component.getAmount() * 100d / total)));
        return List.copyOf(lines);
    }
    public static void appendSummary(List<Component> lines, FluidStack stack) {
        var full = describe(stack);if (full.isEmpty()) return;
        lines.add(full.getFirst().copy().append(" · ").append(net.createplant.thermal.FluidState.describe(stack)));int components=full.size()-1, shown=Math.min(components,components>3 ? 2 : 3);
        for (int i = 1; i <= shown; i++) lines.add(full.get(i));
        if (shown < components) lines.add(Component.translatable("mixture.create_refinery_n_reactors.more", components - shown));
    }

    /**
     * Every gas in a tank, the largest first. Air counts as the mixture it is (MixtureHelper.airContents), so its nitrogen and oxygen
     * join the same gases held elsewhere; each gas is one line with its share.
     */
    public static boolean appendGasLayers(List<Component> lines, List<FluidStack> layers, int capacity) {
        var merged = new ArrayList<FluidStack>();
        for (var layer : layers) for (var gas : constituents(layer)) {
            var same = merged.stream().filter(seen -> seen.getFluid().isSame(gas.getFluid())).findFirst();
            if (same.isPresent()) same.get().grow(gas.getAmount());
            else merged.add(gas.copy());
        }
        if (merged.size() < 2) return false;
        long total = 0;
        for (var gas : merged) total += gas.getAmount();
        merged.sort(Comparator.comparingInt(FluidStack::getAmount).reversed().thenComparing(gas -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(gas.getFluid())));
        lines.clear();
        lines.add(Component.translatable("mixture.create_refinery_n_reactors.gas_layers", String.format(Locale.ROOT, "%,d", total), String.format(Locale.ROOT, "%,d", capacity)));
        var state=layers.size()==1 ? layers.getFirst() : null;
        if(state!=null)lines.add(net.createplant.thermal.FluidState.describe(state));
        for(var gas:merged)lines.add(share(ChemicalReadout.name(gas),gas.getAmount(),total));
        return true;
    }

    private static Component share(Component name, long amount, long total) {
        return Component.translatable("mixture.create_refinery_n_reactors.component", name, String.format(Locale.ROOT, "%,d", amount),
            String.format(Locale.ROOT, "%.1f", amount * 100d / total));
    }

    /** The gases a layer is made of; air and other mixtures by their components. */
    private static List<FluidStack> constituents(FluidStack layer) {
        if (layer.isEmpty()) return List.of();
        return MixtureHelper.composite(layer) ? MixtureHelper.components(layer) : List.of(layer);
    }
}
