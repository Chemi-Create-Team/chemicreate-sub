package net.createplant.mixture;

import java.util.*;
import net.createplant.chemistry.ChemicalReadout;
import net.createplant.chemistry.DistillationProfile;
import net.createplant.registry.CEFluids;
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

    /** Below this share of the whole, a gas is gathered with the other traces under "others". */
    private static final double TRACE = .02;

    /**
     * Every gas in a tank, the largest first. Air counts as the mixture it is, split by the air separation column's yields, so its
     * nitrogen and oxygen join the same gases held elsewhere; each gas is one line with its share, and traces such as argon share one line.
     */
    public static boolean appendGasLayers(List<Component> lines, List<FluidStack> layers) {
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
        lines.add(Component.translatable("mixture.create_refinery_n_reactors.gas_layers", String.format(Locale.ROOT, "%,d", total)));
        long traces = 0;
        for (var gas : merged) {
            if (gas.getAmount() < total * TRACE) { traces += gas.getAmount();continue; }
            lines.add(share(ChemicalReadout.name(gas), gas.getAmount(), total));
        }
        if (traces > 0) lines.add(share(Component.translatable("mixture.create_refinery_n_reactors.others"), traces, total));
        return true;
    }

    private static Component share(Component name, long amount, long total) {
        return Component.translatable("mixture.create_refinery_n_reactors.component", name, String.format(Locale.ROOT, "%,d", amount),
            String.format(Locale.ROOT, "%.1f", amount * 100d / total));
    }

    /** The gases a layer is made of: air by the column's nitrogen, oxygen and argon yields, any other fluid as itself. */
    private static List<FluidStack> constituents(FluidStack layer) {
        if (layer.isEmpty()) return List.of();
        if (!layer.is(CEFluids.AIR_GAS.get())) return List.of(layer);
        var yields = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, Integer>();
        var products = DistillationProfile.PRODUCT;
        for (int i = 0; i < products.length; i++) {
            var gas = products[i] == DistillationProfile.OXYGEN ? CEFluids.OXYGEN_GAS.get() : DistillationProfile.fluid(products[i]);
            yields.put(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(gas), DistillationProfile.PRODUCT_AMOUNT[i]);
        }
        var split = new MixtureContents(yields).resized(layer.getAmount());
        return split.amounts().entrySet().stream()
            .map(entry -> new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(entry.getKey()), entry.getValue())).toList();
    }
}
