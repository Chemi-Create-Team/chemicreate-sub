package net.createplant.thermal;

import net.createplant.registry.CEThermal;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A solution heated alone boils its water off: the volume shrinks and the same solute is left in less liquid. The strength is kept
 * on the stack, and a recipe that takes a concentrated solution needs proportionally less of it. A solution stops at twice its
 * strength; beyond that the evaporation recipes leave crystals.
 */
public final class Concentration {
    public static final int NORMAL=1000, MAXIMUM=2000;
    /** Latent heat of water, in joules per mB (one gram). */
    public static final double JOULES_PER_MB=2257;

    private Concentration() { }

    /** A liquid solution in water whose own boiling point is not tracked, so heating it can only boil the water off. */
    public static boolean applies(FluidStack stack) {
        if(stack.isEmpty() || FluidState.gas(stack) || FluidState.phaseSensitive(stack) || net.createplant.mixture.MixtureHelper.isMixture(stack))return false;
        var info=net.createplant.chemistry.FluidChemicalRegistry.base(stack.getFluid());
        return info!=null && info.aqueous();
    }

    public static int permille(FluidStack stack) { return stack.isEmpty() ? NORMAL : stack.getOrDefault(CEThermal.CONCENTRATION.get(),NORMAL); }
    public static double factor(FluidStack stack) { return permille(stack)/1000d; }

    public static void set(FluidStack stack,int permille) {
        if(permille<=NORMAL)stack.remove(CEThermal.CONCENTRATION.get());
        else stack.set(CEThermal.CONCENTRATION.get(),Math.min(permille,4000));
    }

    /** How many mB of water the solution can still lose before it is as strong as it gets. */
    public static int removable(FluidStack stack) {
        return (int)Math.max(0,Math.floor(stack.getAmount()*(1-permille(stack)/(double)MAXIMUM)));
    }

    /** Takes {@code water} mB out of the stack, keeping its solute in what is left. */
    public static void boilOff(FluidStack stack,int water) {
        int before=stack.getAmount(), after=before-water;
        if(water<=0 || after<=0)return;
        long strength=Math.round((double)permille(stack)*before/after);
        stack.setAmount(after);
        set(stack,(int)Math.min(strength,MAXIMUM));
    }

    /** Strength of two solutions poured together: the solute adds up over the volume. */
    public static int mixed(FluidStack a,int amountA,FluidStack b,int amountB) {
        long total=(long)amountA+amountB;
        return total<=0 ? NORMAL : (int)Math.round(((double)permille(a)*amountA+(double)permille(b)*amountB)/total);
    }
}
