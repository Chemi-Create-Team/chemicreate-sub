package net.createplant.thermal;

import net.createplant.registry.CEThermal;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** A stream that is partly liquid and partly gas (docs/two-phase-flow.md). Temperature and pressure stay rounded to their levels; the phase mix does not. */
public final class TwoPhase {
    /** A stream's own phase is the stack's amount unit. {@code kelvin} is the boiling point the two parts leave at. */
    public record Split(FluidStack liquid,FluidStack gas,double kelvin) { }

    private TwoPhase() { }

    public static boolean is(FluidStack stack) { return !stack.isEmpty() && stack.has(CEThermal.VAPOUR.get()); }
    public static double share(FluidStack stack) { return stack.getOrDefault(CEThermal.VAPOUR.get(),0)/8d; }
    static double shareOrPhase(FluidStack stack) { return is(stack) ? share(stack) : FluidState.gas(stack) ? 1 : 0; }

    public static FluidStack plain(FluidStack stack) {
        if(!is(stack))return stack;
        var result=stack.copy();
        result.remove(CEThermal.VAPOUR.get());
        return result;
    }

    /** Marks a stream that is still changing phase, with the nearest representable gas share. */
    public static FluidStack mark(FluidStack out,double quality) {
        if(out.isEmpty() || !(quality>0 && quality<1) || !FluidState.phaseSensitive(out))return out;
        int eighths=Math.clamp((int)Math.round(quality*8),1,7);
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

    /**
     * Pours a two-phase stream into a liquid and a gas buffer: the liquid part into one, the gas part into the other, scaled down until both fit.
     * Returns the amount of the stream taken; nothing changes unless {@code action} executes.
     */
    public static int divide(FluidStack stream,ThermalFluidTank liquidTank,ThermalFluidTank gasTank,IFluidHandler.FluidAction action) {
        int units=stream.getAmount();
        for(int attempt=0;attempt<4 && units>0;attempt++){
            var split=split(stream,units);
            int fitLiquid=split.liquid().isEmpty() ? 0 : liquidTank.fillAt(split.liquid(),split.kelvin(),0,IFluidHandler.FluidAction.SIMULATE);
            int fitGas=split.gas().isEmpty() ? 0 : gasTank.fillAt(split.gas(),split.kelvin(),1,IFluidHandler.FluidAction.SIMULATE);
            if(fitLiquid==split.liquid().getAmount() && fitGas==split.gas().getAmount()){
                if(action.execute()){
                    if(!split.liquid().isEmpty())liquidTank.fillAt(split.liquid(),split.kelvin(),0,IFluidHandler.FluidAction.EXECUTE);
                    if(!split.gas().isEmpty())gasTank.fillAt(split.gas(),split.kelvin(),1,IFluidHandler.FluidAction.EXECUTE);
                }
                return units;
            }
            double scale=Math.min(split.liquid().isEmpty() ? 1 : (double)fitLiquid/split.liquid().getAmount(),split.gas().isEmpty() ? 1 : (double)fitGas/split.gas().getAmount());
            units=(int)(units*scale);
        }
        return 0;
    }
}
