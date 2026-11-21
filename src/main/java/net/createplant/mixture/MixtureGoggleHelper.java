package net.createplant.mixture;

import java.util.*;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.chemistry.FluidChemicalRegistry;
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
        lines.add(full.getFirst());int components = full.size() - 1, shown = Math.min(components, components > 3 ? 2 : 3);
        for (int i = 1; i <= shown; i++) lines.add(full.get(i));
        if (shown < components) lines.add(Component.translatable("mixture.create_refinery_n_reactors.more", components - shown));
    }

    /**
     * Every gas in a tank, the lightest layer first: one line per gas with its share, the same gas held in several buffers counted once,
     * and its formula only beside its name.
     */
    public static boolean appendGasLayers(List<Component> lines, List<FluidStack> layers) {
        var merged = new ArrayList<FluidStack>();
        for (var layer : layers) {
            if (layer.isEmpty()) continue;
            var same = merged.stream().filter(seen -> seen.getFluid().isSame(layer.getFluid())).findFirst();
            if (same.isPresent()) same.get().grow(layer.getAmount());
            else merged.add(layer.copy());
        }
        boolean mixed = merged.size() > 1;
        for (var layer : merged) {
            var info = FluidChemicalRegistry.base(layer.getFluid());
            mixed |= info != null && info.formula().contains(" + ");
        }
        if (!mixed) return false;
        long total = 0;
        for (var layer : merged) total += layer.getAmount();
        lines.clear();
        lines.add(Component.translatable("mixture.create_refinery_n_reactors.gas_layers", String.format(Locale.ROOT, "%,d", total)));
        for (int i = merged.size() - 1; i >= 0; i--) {
            var layer = merged.get(i);
            lines.add(Component.translatable("mixture.create_refinery_n_reactors.component", ChemicalReadout.name(layer),
                String.format(Locale.ROOT, "%,d", layer.getAmount()), String.format(Locale.ROOT, "%.1f", layer.getAmount() * 100d / total)));
        }
        return true;
    }
}
