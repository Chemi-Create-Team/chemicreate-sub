package net.createplant.thermal;

import net.createplant.registry.CEThermal;
import net.neoforged.neoforge.fluids.FluidStack;

/** A stream that is partly liquid and partly gas (docs/two-phase-flow.md). Temperature and pressure stay rounded to their levels; the phase mix does not. */
public final class TwoPhase {
    /** A stream's own phase is the stack's amount unit. {@code kelvin} is the boiling point the two parts leave at. */
    public record Split(FluidStack liquid,FluidStack gas,double kelvin) { }

    private TwoPhase() { }

    public static boolean is(FluidStack stack) { return !stack.isEmpty() && stack.has(CEThermal.VAPOUR.get()); }
    public static double share(FluidStack stack) { return stack.getOrDefault(CEThermal.VAPOUR.get(),0)/8d; }

    public static FluidStack plain(FluidStack stack) {
        if(!is(stack))return stack;
        var result=stack.copy();
        result.remove(CEThermal.VAPOUR.get());
        return result;
    }

    /** Marks a stream that left a boiling or condensing content with the gas share it had, rounded to an eighth; a nearly pure phase stays single. */
    public static FluidStack mark(FluidStack out,double quality) {
        int eighths=(int)Math.round(quality*8);
        if(out.isEmpty() || eighths<1 || eighths>7 || !FluidState.phaseSensitive(out))return out;
        var result=out.copy();
        result.set(CEThermal.VAPOUR.get(),eighths);
        return result;
    }

    /** Divides {@code units} of the stream into its liquid and gas. What does not convert in whole batches stays in the stream's own phase, so no matter is made. */
    public static Split split(FluidStack stream,int units) {
        var base=plain(stream);
        int pressure=FluidState.pressure(base);
        double boil=FluidState.boilingKelvin(base,pressure);
        if(!Double.isFinite(boil))boil=FluidState.kelvin(FluidState.temperature(base));
        int liquidLevel=FluidState.levelFor(base,boil,pressure,false), gasLevel=FluidState.levelFor(base,boil,pressure,true);
        double gasShare=share(stream);
        if(!FluidState.gas(base)) {
            var change=PhaseChange.transfer(base,gasLevel,pressure);
            int batches=change.batches((int)(units*gasShare));
            return new Split(FluidState.with(base,liquidLevel,pressure).copyWithAmount(units-batches*change.consumed()),change.output(batches),boil);
        }
        var change=PhaseChange.transfer(base,liquidLevel,pressure);
        int batches=change.batches((int)(units*(1-gasShare)));
        return new Split(change.output(batches),FluidState.with(base,gasLevel,pressure).copyWithAmount(units-batches*change.consumed()),boil);
    }
}
