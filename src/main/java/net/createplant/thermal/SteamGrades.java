package net.createplant.thermal;

import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.createplant.registry.CEThermal;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed turbine work and wear per game mB; one steam mB returns one water mB. */
public enum SteamGrades {
    ORDINARY(2258,1), HIGH_PRESSURE(2508,2), SUPERHEATED(3008,4), EXHAUST(2008,1);

    private final double joulesPerMb,wearPerMb;
    SteamGrades(double joulesPerMb,double wearPerMb){this.joulesPerMb=joulesPerMb;this.wearPerMb=wearPerMb;}
    public double joulesPerMb(){return joulesPerMb;}
    public double workPerMb(boolean lowPressure) {
        if (!lowPressure) return joulesPerMb - EXHAUST.joulesPerMb;
        return this == ORDINARY ? 375 : joulesPerMb - 1758;
    }
    public double condenserHeatPerMb() { return joulesPerMb - workPerMb(true); }
    public double wearPerMb(){return wearPerMb;}
    public static SteamGrades of(FluidStack stack) {
        if (stack.is(CEFluids.STEAM.get())) return stack.getOrDefault(CEThermal.EXPANDED_STEAM.get(), false) ? EXHAUST : ORDINARY;
        if (stack.is(CEProcessFluids.HIGH_PRESSURE_STEAM.get())) return HIGH_PRESSURE;
        return stack.is(CEProcessFluids.SUPERHEATED_STEAM.get()) ? SUPERHEATED : null;
    }
    public Fluid fluid(){return switch(this){case ORDINARY,EXHAUST->CEFluids.STEAM.get();case HIGH_PRESSURE->CEProcessFluids.HIGH_PRESSURE_STEAM.get();case SUPERHEATED->CEProcessFluids.SUPERHEATED_STEAM.get();};}
    public FluidStack stack(int amount) {
        var stack = new FluidStack(fluid(), amount);
        if (this == EXHAUST && !stack.isEmpty()) stack.set(CEThermal.EXPANDED_STEAM.get(), true);
        return stack;
    }
    public static FluidStack outlet(FluidStack input, int amount, boolean lowPressure) {
        var result = new FluidStack(lowPressure ? Fluids.WATER : CEFluids.STEAM.get(), amount);
        if (result.isEmpty()) return result;
        FluidThermal.processComponents(input, result);
        if (!lowPressure) result.set(CEThermal.EXPANDED_STEAM.get(), true);
        return result;
    }
}
