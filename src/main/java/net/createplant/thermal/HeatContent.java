package net.createplant.thermal;

import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The real temperature of one inventory's contents (docs/internal-temperature.md). The stack itself keeps carrying the 17 levels;
 * this holds the kelvin behind the level, the share of a boiling content that has already changed phase, and the heat left over
 * from rounding when the contents were emptied.
 */
public final class HeatContent {
    private static final double EPSILON=1e-9, MINIMUM=FluidState.kelvin(0), MAXIMUM=FluidState.kelvin(FluidState.MAX);
    /** What a heating or cooling step produced; {@code left} is the energy the content could not take. */
    public record Outcome(FluidStack stack,boolean structural,double left) { }

    private double kelvin=Double.NaN,quality=Double.NaN,residual,lastOutKelvin=Double.NaN,lastOutQuality=Double.NaN;
    private int level=-1;
    private Fluid fluid;

    // ponytail: pure arithmetic on doubles, so the heat balance can be checked without a world.
    public static double mix(double capacityA,double kelvinA,double capacityB,double kelvinB) {
        double total=capacityA+capacityB;
        return total<=0 ? kelvinB : (capacityA*kelvinA+capacityB*kelvinB)/total;
    }
    public static double remainder(double capacityAll,double kelvinAll,double capacityOut,double kelvinOut,double capacityLeft) {
        return capacityLeft<=0 ? Double.NaN : (capacityAll*kelvinAll-capacityOut*kelvinOut)/capacityLeft;
    }
    public static double cap(double residual,double limit) { return Math.clamp(residual,-Math.abs(limit),Math.abs(limit)); }
    public static double clampKelvin(double kelvin) { return Math.clamp(kelvin,MINIMUM,MAXIMUM); }

    private boolean valid(FluidStack stack) {
        return !Double.isNaN(kelvin) && !stack.isEmpty() && (fluid==null || fluid==stack.getFluid()) && level==FluidState.temperature(stack);
    }
    /** The real temperature, or the level's own temperature when the contents were changed from outside. */
    public double kelvin(FluidStack stack) {
        return stack.isEmpty() ? FluidState.kelvin(FluidState.AMBIENT) : valid(stack) ? kelvin : FluidState.kelvin(FluidState.temperature(stack));
    }
    /** Share of the content that is gas: 0 to 1. Only a content at its boiling point holds a value between. */
    public double quality(FluidStack stack) {
        return valid(stack) && !Double.isNaN(quality) ? quality : FluidState.gas(stack) ? 1 : 0;
    }
    public double residual() { return residual; }
    /** The real temperature and gas share the contents had when the last amount left them. */
    public double lastOutKelvin() { return lastOutKelvin; }
    public double lastOutQuality() { return lastOutQuality; }
    public void set(FluidStack stack,double newKelvin,double newQuality) {
        if(stack.isEmpty()) { kelvin=Double.NaN;quality=Double.NaN;level=-1;fluid=null;return; }
        kelvin=clampKelvin(newKelvin);quality=newQuality;level=FluidState.temperature(stack);fluid=stack.getFluid();
    }
    public void set(FluidStack stack,double newKelvin) { set(stack,newKelvin,Double.NaN); }
    public void forget() { kelvin=Double.NaN;quality=Double.NaN;level=-1;fluid=null;residual=0; }
    public static double capacity(FluidStack stack) { return FluidThermal.heatCapacity(stack); }

    /** Temperature after {@code amount} mB of {@code incoming} (at {@code incomingKelvin}, or its level's when NaN) joins {@code stored}. */
    public double enter(FluidStack stored,FluidStack incoming,int amount,double incomingKelvin,boolean commit) {
        double ti=Double.isNaN(incomingKelvin) ? FluidState.kelvin(FluidState.temperature(incoming)) : incomingKelvin;
        double ci=capacity(incoming.copyWithAmount(amount));
        boolean empty=stored.isEmpty();
        double cs=empty ? 0 : capacity(stored), ts=empty ? 0 : kelvin(stored);
        double energy=cs*ts+ci*ti+(empty ? residual : 0), total=cs+ci;
        double t=total<=0 ? ti : energy/total;
        if(empty) {
            int own=FluidState.temperature(incoming);
            t=Math.clamp(t,Math.min(ti,FluidState.lowerBound(own)),Math.max(ti,FluidState.upperBound(own)));
        }
        t=clampKelvin(t);
        if(commit && empty)residual=total<=0 ? residual : energy-total*t;
        return t;
    }

    /** Called after {@code out} left {@code before}; moves the remaining contents to the temperature that keeps the heat balance. Returns whether their level changed. */
    public boolean leave(FluidStack before,FluidStack out,FluidStack left) {
        double all=capacity(before), tAll=kelvin(before), cOut=capacity(out), tOut=FluidState.kelvin(FluidState.temperature(out));
        double q=quality(before);
        // Part way through boiling or condensing, what leaves is the same mix of liquid and gas at the boiling point, so the rest
        // keeps its temperature and its share, and the share of the latent heat that leaves is the share of the amount.
        boolean partial=q>EPSILON && q<1-EPSILON;
        if(partial)tOut=tAll;
        lastOutKelvin=tAll;lastOutQuality=q;
        if(left.isEmpty()) {
            int own=FluidState.temperature(before);
            residual=cap(residual+all*tAll-cOut*tOut,all*(FluidState.upperBound(own)-FluidState.lowerBound(own))/2);
            kelvin=Double.NaN;quality=Double.NaN;level=-1;fluid=null;
            return false;
        }
        double t=clampKelvin(remainder(all,tAll,cOut,tOut,capacity(left)));
        if(Double.isNaN(t))t=tAll;
        // A liquid never ends above its boiling point or a gas below it; rounding the stream to a level cannot push it there.
        if(FluidState.phaseSensitive(left)) {
            double boil=FluidState.boilingKelvin(left,FluidState.pressure(left));
            if(Double.isFinite(boil))t=FluidState.gas(left) ? Math.max(t,boil) : Math.min(t,boil);
        }
        int next=FluidState.levelFor(left,t,FluidState.pressure(left),FluidState.gas(left));
        boolean changed=next!=FluidState.temperature(left);
        if(changed)FluidState.setTemperature(left,next);
        set(left,t,q);
        return changed;
    }

    public static double latent(FluidStack stack) {
        double mass=FluidMaterials.mass(stack), molar=FluidMaterials.molarMass(stack);
        return molar<=0 ? 0 : mass/molar*FluidState.latentMolar(stack);
    }

    private static FluidStack relevel(FluidStack stack,double t,boolean gas) {
        int next=FluidState.levelFor(stack,t,FluidState.pressure(stack),gas);
        if(next==FluidState.temperature(stack))return stack;
        var result=stack.copy();FluidState.setTemperature(result,next);return result;
    }

    private static FluidStack convert(FluidStack stack,double t,boolean toGas,ToIntFunction<FluidStack> room) {
        int pressure=FluidState.pressure(stack);
        var change=PhaseChange.transfer(stack,FluidState.levelFor(stack,t,pressure,toGas),pressure);
        int batches=change.batches(stack.getAmount());
        if(batches*change.consumed()!=stack.getAmount() || (long)batches*change.produced()>room.applyAsInt(change.prototype()))return null;
        var result=stack.copy();
        result.setAmount(batches*change.produced());
        result.applyComponents(change.prototype().getComponentsPatch());
        // A level back at the substance's own state is stored by removing the data, which a patch does not carry.
        FluidState.setTemperature(result,FluidState.temperature(change.prototype()));
        FluidState.setPressure(result,FluidState.pressure(change.prototype()));
        return result;
    }

    /**
     * Adds (positive) or removes (negative) heat. A liquid stops at its boiling point and takes the latent heat before it turns to
     * gas, a gas does the same when cooled. Heat beyond {@code ceiling} or below {@code floor} is not taken.
     */
    public Outcome add(FluidStack input,double joules,double floor,double ceiling,ToIntFunction<FluidStack> room) {
        FluidStack stack=input.copy();
        boolean structural=false;
        double t=kelvin(input), q=quality(input);
        boolean gas=FluidState.gas(input);
        for(int guard=0;guard<16 && Math.abs(joules)>1e-6 && !stack.isEmpty();guard++) {
            double c=capacity(stack);
            if(c<=0)break;
            boolean heating=joules>0;
            double boil=FluidState.phaseSensitive(stack) ? FluidState.boilingKelvin(stack,FluidState.pressure(stack)) : Double.POSITIVE_INFINITY;
            boolean boiling=boil>floor && boil<ceiling && heating!=gas;
            double stop=boiling ? boil : heating ? ceiling : floor;
            if(heating ? t<stop-EPSILON : t>stop+EPSILON) {
                double step=Math.min(Math.abs(joules)/c,Math.abs(stop-t));
                t+=heating ? step : -step;
                joules-=Math.signum(joules)*step*c;
                stack=relevel(stack,t,gas);
                continue;
            }
            if(!boiling)break;
            double latent=latent(stack);
            if(latent<=0)break;
            double goal=heating ? 1 : 0, use=Math.min(Math.abs(joules),Math.abs(goal-q)*latent);
            q+=heating ? use/latent : -use/latent;
            joules-=Math.signum(joules)*use;
            if(Math.abs(goal-q)>EPSILON)break;
            // Without a room function nothing limits the converted amount; an amount that does not convert whole stays at its boiling point.
            var converted=convert(stack,t,heating,room==null ? ignored->Integer.MAX_VALUE : room);
            if(converted==null)break;
            stack=converted;structural=true;q=goal;gas=heating;
        }
        set(stack,t,q);
        return new Outcome(stack,structural,joules);
    }

    /** What a stream of {@code sample} (at {@code kelvin}) is like after taking {@code joules}, without touching any inventory. */
    public record Shift(double kelvin,boolean gas,double quality) { }
    public static Shift shift(FluidStack sample,double kelvin,double joules) { return shift(sample,kelvin,joules,MINIMUM); }
    /** As above, but the stream is not cooled below {@code floor}. */
    public static Shift shift(FluidStack sample,double kelvin,double joules,double floor) {
        var scratch=new HeatContent();
        scratch.set(sample,kelvin);
        var outcome=scratch.add(sample,joules,floor,MAXIMUM,null);
        return new Shift(scratch.kelvin(outcome.stack()),scratch.quality(outcome.stack())>=.5,scratch.quality(outcome.stack()));
    }

    /** The heat (always positive) {@code sample} at {@code from} gives or takes on the way to {@code to}, latent heat included. */
    public static double heatBetween(FluidStack sample,double from,double to) {
        if(Math.abs(to-from)<EPSILON)return 0;
        boolean heating=to>from;
        double big=1e9;
        var scratch=new HeatContent();
        scratch.set(sample,from);
        var outcome=scratch.add(sample,heating ? big : -big,heating ? MINIMUM : to,heating ? to : MAXIMUM,null);
        return big-Math.abs(outcome.left());
    }

    public void write(CompoundTag tag) {
        if(!Double.isNaN(kelvin)) {
            tag.putDouble("Kelvin",kelvin);
            tag.putInt("Level",level);
            if(!Double.isNaN(quality))tag.putDouble("Quality",quality);
        }
        if(residual!=0)tag.putDouble("ResidualHeat",residual);
    }

    /** The saved temperature applies to whatever is in the inventory while its level is still the saved one. */
    public void read(CompoundTag tag) {
        forget();
        if(tag.contains("ResidualHeat") && Double.isFinite(tag.getDouble("ResidualHeat")))residual=tag.getDouble("ResidualHeat");
        if(tag.contains("Kelvin") && Double.isFinite(tag.getDouble("Kelvin"))) {
            kelvin=clampKelvin(tag.getDouble("Kelvin"));level=tag.getInt("Level");
            quality=tag.contains("Quality") && Double.isFinite(tag.getDouble("Quality")) ? Math.clamp(tag.getDouble("Quality"),0,1) : Double.NaN;
        }
    }
}
