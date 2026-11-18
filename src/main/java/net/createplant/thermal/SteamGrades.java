package net.createplant.thermal;

import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed turbine work and wear per game mB; one steam mB returns one water mB. */
public enum SteamGrades {
    ORDINARY(2008,1), HIGH_PRESSURE(2508,2), SUPERHEATED(3008,4);

    public static final double CONDENSER_JOULES_PER_MB=1758;
    private final double joulesPerMb,wearPerMb;
    SteamGrades(double joulesPerMb,double wearPerMb){this.joulesPerMb=joulesPerMb;this.wearPerMb=wearPerMb;}
    public double joulesPerMb(){return joulesPerMb;}
    public double workPerMb(boolean lowPressure){return joulesPerMb-(lowPressure ? CONDENSER_JOULES_PER_MB : ORDINARY.joulesPerMb);}
    public double wearPerMb(){return wearPerMb;}
    public static SteamGrades of(FluidStack stack){return stack.is(CEFluids.STEAM.get()) ? ORDINARY : stack.is(CEProcessFluids.HIGH_PRESSURE_STEAM.get()) ? HIGH_PRESSURE : stack.is(CEProcessFluids.SUPERHEATED_STEAM.get()) ? SUPERHEATED : null;}
    public Fluid fluid(){return switch(this){case ORDINARY->CEFluids.STEAM.get();case HIGH_PRESSURE->CEProcessFluids.HIGH_PRESSURE_STEAM.get();case SUPERHEATED->CEProcessFluids.SUPERHEATED_STEAM.get();};}
    public static FluidStack outlet(FluidStack input,int amount,boolean lowPressure){var result=new FluidStack(lowPressure ? Fluids.WATER : CEFluids.STEAM.get(),amount);FluidThermal.processComponents(input,result);return result;}
}
