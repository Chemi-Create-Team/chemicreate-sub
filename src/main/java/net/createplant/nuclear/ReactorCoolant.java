package net.createplant.nuclear;

import net.createplant.registry.CEProcessFluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Numeric coolant properties remain inside the RBMK; external fluid stacks carry grades only. */
public final class ReactorCoolant {
    public static final double CP=4180,LATENT=2257000,AMBIENT_KELVIN=293.15,OPERATING_INLET_KELVIN=543.15;
    private ReactorCoolant(){ }
    public static boolean accepts(FluidStack stack){return stack.is(net.minecraft.world.level.material.Fluids.WATER) || stack.is(CEProcessFluids.PRESSURISED_WATER.get());}
    public static double inletSpecific(FluidStack stack){return CP*(stack.is(CEProcessFluids.PRESSURISED_WATER.get()) ? OPERATING_INLET_KELVIN : AMBIENT_KELVIN);}
}
