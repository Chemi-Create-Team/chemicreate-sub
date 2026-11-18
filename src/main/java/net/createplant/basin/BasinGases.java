package net.createplant.basin;

import net.createplant.thermal.FluidMaterials;
import net.neoforged.neoforge.fluids.FluidStack;

/** Gases require closed process equipment. */
public final class BasinGases {
    private BasinGases() { }

    public static boolean gas(FluidStack stack) { return !stack.isEmpty() && FluidMaterials.gas(stack.getFluid()); }
}
