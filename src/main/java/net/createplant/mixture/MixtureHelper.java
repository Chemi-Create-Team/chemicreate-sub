package net.createplant.mixture;

import java.util.Map;
import net.createplant.registry.CEMixtures;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MixtureHelper {
    private MixtureHelper() { }
    public static boolean isMixture(FluidStack stack) { return stack.getFluid() instanceof MixtureFluid; }
    public static MixtureContents extractContents(FluidStack stack) {
        if (stack.isEmpty()) return MixtureContents.EMPTY;
        if (isAir(stack)) return airContents(stack.getAmount());
        if (!isMixture(stack)) return new MixtureContents(Map.of(BuiltInRegistries.FLUID.getKey(source(stack.getFluid())), stack.getAmount()));
        var contents = stack.get(CEMixtures.MIXTURE_CONTENTS.get());
        if (contents == null || contents.totalAmount() != stack.getAmount()) throw new IllegalArgumentException("Mixture component volume does not match its stack");
        return contents;
    }
    /** Air is a mixture too: 780 nitrogen, 210 oxygen and 10 other gases (argon and the rest) in every 1000 mB. */
    public static boolean composite(FluidStack stack) { return isMixture(stack) || isAir(stack); }
    private static boolean isAir(FluidStack stack) { return !stack.isEmpty() && source(stack.getFluid()).isSame(net.createplant.registry.CEFluids.AIR_GAS.get()); }
    public static MixtureContents airContents(int amount) {
        if (amount <= 0) return MixtureContents.EMPTY;
        return new MixtureContents(Map.of(BuiltInRegistries.FLUID.getKey(net.createplant.registry.CEFluids.NITROGEN_GAS.get()), 780,
            BuiltInRegistries.FLUID.getKey(net.createplant.registry.CEFluids.OXYGEN_GAS.get()), 210,
            BuiltInRegistries.FLUID.getKey(CEMixtures.OTHER_GASES.get()), 10)).resized(amount);
    }
    private static Fluid source(Fluid fluid) { return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid; }
    /** A gas mixture rides the gas carrier and a liquid mixture the liquid one; gases and liquids never share a stack. */
    public static FluidStack createStack(MixtureContents contents, boolean unwrapIfPure) {
        int total = contents.totalAmount();if (total == 0) return FluidStack.EMPTY;
        int gases = 0;
        for (var id : contents.amounts().keySet()) {
            if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalArgumentException("Unknown mixture component " + id);
            var fluid = BuiltInRegistries.FLUID.get(id);
            if (fluid instanceof MixtureFluid || fluid.defaultFluidState().isEmpty()) throw new IllegalArgumentException("Invalid nested or empty mixture component " + id);
            if (gasFluid(fluid)) gases++;
        }
        if (gases != 0 && gases != contents.amounts().size()) throw new IllegalArgumentException("A mixture cannot hold both gases and liquids");
        if (unwrapIfPure && contents.amounts().size() == 1) return new FluidStack(BuiltInRegistries.FLUID.get(contents.amounts().keySet().iterator().next()), total);
        var stack = new FluidStack(gases == 0 ? CEMixtures.CHEMICAL_MIXTURE.get() : CEMixtures.GAS_MIXTURE.get(), total);stack.set(CEMixtures.MIXTURE_CONTENTS.get(), contents);return stack;
    }
    private static boolean gasFluid(Fluid fluid) { return FluidHazards.of(new FluidStack(fluid, 1)).gas() || fluid.getFluidType().isLighterThanAir(); }
    /** A gas, pure or mixed. */
    public static boolean gas(FluidStack stack) {
        if (stack.isEmpty()) return false;
        return isMixture(stack) ? stack.getFluidType() == CEMixtures.GAS_TYPE.get() : gasFluid(stack.getFluid());
    }
    /** A gas mixture whose every component is a real gas, or a pure gas. */
    public static boolean isMixableGas(FluidStack stack) {
        if (!gas(stack)) return false;
        if (!isMixture(stack)) return true;
        try {
            return extractContents(stack).amounts().keySet().stream().allMatch(id -> BuiltInRegistries.FLUID.containsKey(id) && !(BuiltInRegistries.FLUID.get(id) instanceof MixtureFluid)
                && gasFluid(BuiltInRegistries.FLUID.get(id)));
        } catch (IllegalArgumentException error) { return false; }
    }
    public static MixtureData data(FluidStack stack) {
        if (stack.isEmpty()) return MixtureData.EMPTY;
        if (isMixture(stack)) {
            var kept = stack.getOrDefault(CEMixtures.MIXTURE_DATA.get(), MixtureData.EMPTY).forContents(extractContents(stack));
            var heat = stack.get(net.createplant.registry.CEThermal.HEAT_GRADE.get());if (heat == null) return kept;
            var patches = new java.util.TreeMap<net.minecraft.resources.ResourceLocation, net.minecraft.core.component.DataComponentPatch>();
            for (var id : extractContents(stack).amounts().keySet()) {
                var part = new FluidStack(BuiltInRegistries.FLUID.get(id).builtInRegistryHolder(), 1, kept.components().getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY));
                part.set(net.createplant.registry.CEThermal.HEAT_GRADE.get(), heat);patches.put(id, part.getComponentsPatch());
            }
            return new MixtureData(patches);
        }
        var copy = stack.copy();net.createplant.nuclear.RadiationSources.stamp(copy);
        if (isAir(stack)) {
            var patches = new java.util.TreeMap<net.minecraft.resources.ResourceLocation, net.minecraft.core.component.DataComponentPatch>();
            for (var id : extractContents(stack).amounts().keySet()) patches.put(id, copy.getComponentsPatch());
            return new MixtureData(patches);
        }
        return new MixtureData(Map.of(BuiltInRegistries.FLUID.getKey(source(copy.getFluid())), copy.getComponentsPatch()));
    }
    public static java.util.List<FluidStack> components(FluidStack stack) {
        var contents = extractContents(stack);var data = data(stack);
        return contents.amounts().entrySet().stream().map(entry -> new FluidStack(BuiltInRegistries.FLUID.get(entry.getKey()).builtInRegistryHolder(), entry.getValue(),
            data.components().getOrDefault(entry.getKey(), net.minecraft.core.component.DataComponentPatch.EMPTY))).toList();
    }
    public static FluidStack createStack(MixtureContents contents, MixtureData data, boolean unwrap) {
        var result = createStack(contents, unwrap);if (result.isEmpty()) return result;
        if (isMixture(result)) {
            var kept = data.forContents(contents);if (!kept.components().isEmpty()) result.set(CEMixtures.MIXTURE_DATA.get(), kept);
        } else result.applyComponents(data.components().getOrDefault(contents.amounts().keySet().iterator().next(), net.minecraft.core.component.DataComponentPatch.EMPTY));
        return result;
    }
    public static FluidStack merge(FluidStack first, FluidStack second, boolean unwrap) {
        if (first.isEmpty()) return createStack(extractContents(second), data(second), unwrap);
        var a = extractContents(first);var b = extractContents(second);var patches = new java.util.TreeMap<>(data(first).components());
        var incoming = data(second);
        for (var entry : b.amounts().entrySet()) {
            var id = entry.getKey();var patch = incoming.components().getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY);
            if (a.amounts().containsKey(id)) {
                var original = new FluidStack(BuiltInRegistries.FLUID.get(id).builtInRegistryHolder(), a.amounts().get(id), patches.getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY));
                var added = new FluidStack(BuiltInRegistries.FLUID.get(id).builtInRegistryHolder(), entry.getValue(), patch);
                if (!net.createplant.thermal.FluidThermal.mixable(original, added)) throw new IllegalArgumentException("Incompatible properties of mixture component " + id);
                patch = net.createplant.thermal.FluidThermal.mix(original, added, added.getAmount()).getComponentsPatch();
            }
            patches.put(id, patch);
        }
        return createStack(a.merge(b), new MixtureData(patches), unwrap);
    }
    public static boolean isMixableLiquid(FluidStack stack) {
        if (stack.isEmpty()) return false;
        if (!isMixture(stack)) return !FluidHazards.of(stack).gas() && !stack.getFluidType().isLighterThanAir();
        try {
            return extractContents(stack).amounts().entrySet().stream().allMatch(entry -> {
                if (!BuiltInRegistries.FLUID.containsKey(entry.getKey())) return false;
                var fluid = BuiltInRegistries.FLUID.get(entry.getKey());
                return !(fluid instanceof MixtureFluid) && !fluid.defaultFluidState().isEmpty() && isMixableLiquid(new FluidStack(fluid, entry.getValue()));
            });
        } catch (IllegalArgumentException error) { return false; }
    }
    public static FluidStack portion(FluidStack stack, int amount) {
        return isMixture(stack) ? createStack(extractContents(stack).split(amount).extracted(), data(stack), false) : stack.copyWithAmount(amount);
    }
    public static FluidStack remainder(FluidStack stack, int removed) {
        if (!isMixture(stack)) return stack.copyWithAmount(stack.getAmount() - removed);
        return createStack(extractContents(stack).split(removed).remaining(), data(stack), false);
    }

}
