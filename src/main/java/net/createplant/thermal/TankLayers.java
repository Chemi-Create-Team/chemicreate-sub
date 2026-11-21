package net.createplant.thermal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import net.createplant.mixture.MixtureContents;
import net.createplant.mixture.MixtureHelper;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/** Common bottom-to-top ordering for a vessel's visible layers and its outlets. */
public final class TankLayers {
    public record Removal(FluidStack sample, FluidStack remaining) { }
    private TankLayers() { }

    public static double density(FluidStack stack) {
        if (FluidHazards.of(stack).gas()) return FluidMaterials.molarMass(stack);
        return FluidThermal.properties(stack).kilogramsPerLitre();
    }

    public static List<FluidStack> ordered(List<FluidStack> fluids) {
        var layers = new ArrayList<FluidStack>();
        for (var fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) continue;
            if (MixtureHelper.composite(fluid)) layers.addAll(MixtureHelper.components(fluid));
            else layers.add(fluid);
        }
        layers.sort(Comparator.comparingDouble(TankLayers::density).reversed()
            .thenComparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid())));
        return List.copyOf(layers);
    }

    public static Removal remove(FluidStack mixture, FluidStack layer, int amount) {
        if (amount <= 0 || layer.isEmpty() || !MixtureHelper.composite(mixture)) return new Removal(FluidStack.EMPTY, mixture);
        var id = BuiltInRegistries.FLUID.getKey(layer.getFluid());
        var contents = MixtureHelper.extractContents(mixture);
        int extracted = Math.min(amount, contents.amounts().getOrDefault(id, 0));
        if (extracted <= 0) return new Removal(FluidStack.EMPTY, mixture);
        var remaining = new TreeMap<>(contents.amounts());
        remaining.put(id, remaining.get(id) - extracted);
        var data = MixtureHelper.data(mixture);
        var sample = new FluidStack(layer.getFluid().builtInRegistryHolder(), extracted,
            data.components().getOrDefault(id, net.minecraft.core.component.DataComponentPatch.EMPTY));
        return new Removal(sample, MixtureHelper.createStack(new MixtureContents(remaining), data, true));
    }
}
