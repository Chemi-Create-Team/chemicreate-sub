package net.createplant.mixture;

import java.util.*;
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
}
