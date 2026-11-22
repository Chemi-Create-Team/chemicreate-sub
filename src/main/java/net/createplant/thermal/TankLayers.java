package net.createplant.thermal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.createplant.mixture.MixtureHelper;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/** Common bottom-to-top ordering for vessel layers, with one layer per gas stock. */
public final class TankLayers {
    private TankLayers() { }

    public static double density(FluidStack stack) {
        if (FluidHazards.of(stack).gas()) return FluidMaterials.molarMass(stack);
        return FluidThermal.properties(stack).kilogramsPerLitre();
    }

    public static List<FluidStack> ordered(List<FluidStack> fluids) {
        var layers = new ArrayList<FluidStack>();
        for (var fluid : fluids) {
            if (fluid == null || fluid.isEmpty()) continue;
            if (MixtureHelper.isMixture(fluid) && !MixtureHelper.gas(fluid)) layers.addAll(MixtureHelper.components(fluid));
            else layers.add(fluid);
        }
        layers.sort(Comparator.comparingDouble(TankLayers::density).reversed()
            .thenComparing(fluid -> BuiltInRegistries.FLUID.getKey(fluid.getFluid())));
        return List.copyOf(layers);
    }

}
