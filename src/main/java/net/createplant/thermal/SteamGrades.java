package net.createplant.thermal;

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
        if(!stack.is(Fluids.WATER) || !FluidState.gas(stack))return null;
        if(stack.getOrDefault(CEThermal.EXPANDED_STEAM.get(),false))return EXHAUST;
        if(FluidState.pressure(stack)>=12)return FluidState.temperature(stack)==FluidState.MAX ? SUPERHEATED : HIGH_PRESSURE;
        return ORDINARY;
    }
    public FluidForm form(){return switch(this){case ORDINARY,EXHAUST->FluidForm.STEAM;case HIGH_PRESSURE->FluidForm.HIGH_PRESSURE_STEAM;case SUPERHEATED->FluidForm.SUPERHEATED_STEAM;};}
    public FluidStack stack(int amount) {
        var stack = form().stack(amount);
        if (this == EXHAUST && !stack.isEmpty()) stack.set(CEThermal.EXPANDED_STEAM.get(), true);
        return stack;
    }
    public static FluidStack outlet(FluidStack input, int amount, boolean lowPressure) {
        var result = lowPressure ? new FluidStack(Fluids.WATER, amount) : FluidForm.STEAM.stack(amount);
        if (result.isEmpty()) return result;
        FluidThermal.processComponents(input, result);
        if (!lowPressure) result.set(CEThermal.EXPANDED_STEAM.get(), true);
        return result;
    }
}
