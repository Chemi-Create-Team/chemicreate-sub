package net.createplant.thermal;

import net.createplant.mixture.MixtureHelper;
import net.createplant.registry.CEThermal;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteamCondensate {
    public static final double BOILING_KELVIN = 373.15;

    private SteamCondensate() { }

    public static boolean water(FluidStack stack) {
        return stack.is(Fluids.WATER) && stack.getOrDefault(CEThermal.CONDENSATE.get(), false);
    }

    public static boolean vapour(FluidStack stack) {
        return SteamGrades.of(stack) != null || MixtureHelper.isMixture(stack) && MixtureHelper.gas(stack)
            && MixtureHelper.components(stack).stream().anyMatch(part -> SteamGrades.of(part) != null);
    }

    public record Split(FluidStack gas, FluidStack water) { }

    /** Steam and water use the same mB basis as the turbine. The caller chooses how much space is available for water. */
    public static Split separate(FluidStack source, double kelvin, int capacity) {
        if (capacity <= 0 || kelvin > BOILING_KELVIN || !vapour(source)) return new Split(source, FluidStack.EMPTY);
        var gasParts = new java.util.ArrayList<FluidStack>();
        int amount = 0;
        for (var part : MixtureHelper.components(source)) {
            int taken = SteamGrades.of(part) == null ? 0 : Math.min(capacity - amount, part.getAmount());
            amount += taken;
            if (taken < part.getAmount()) gasParts.add(part.copyWithAmount(part.getAmount() - taken));
        }
        boolean dried=amount>0 && gasParts.stream().noneMatch(part -> SteamGrades.of(part)!=null);
        var gas=FluidStack.EMPTY;
        for (var part:gasParts) {
            if (dried) part.set(CEThermal.CONDENSATE.get(),true);
            gas=MixtureHelper.merge(gas,part,true);
        }
        var water = amount == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, amount);
        if (!water.isEmpty()) {
            water.set(CEThermal.CONDENSATE.get(), true);
            water = withTemperature(water, Math.min(kelvin, 372.99));
        }
        return new Split(gas, water);
    }

    public static FluidStack withTemperature(FluidStack source, double kelvin) {
        if (source.isEmpty()) return source;
        var result = source.copy();
        var grade = Grade.Heat.of(kelvin);
        var component = CEThermal.HEAT_GRADE.get();
        result.remove(component);
        if (MixtureHelper.isMixture(result) || Grade.Heat.of(FluidThermal.kelvin(result)) != grade) result.set(component, grade.ordinal());
        return result;
    }
}
