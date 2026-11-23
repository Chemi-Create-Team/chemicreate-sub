package net.createplant.thermal;

import java.util.function.Consumer;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

/** Opt-in tank: preserves all other component distinctions and the native callback/save contract. */
public class ThermalFluidTank extends SmartFluidTank {
    private final HeatContent heat=new HeatContent();
    /** The kelvin of the incoming stack during {@link #fillAt}; NaN means the stack's own level. */
    private double entryKelvin=Double.NaN, entryQuality=Double.NaN;
    public ThermalFluidTank(int capacity,Consumer<FluidStack> changed) { super(capacity,changed); }
    /** The real temperature, heat balance and boiling share of the contents. */
    public HeatContent heat(){return heat;}
    public double kelvin(){return heat.kelvin(fluid);}
    /** Fills a stream that carries a real temperature instead of its level's; the tank takes it with its heat. */
    public int fillAt(FluidStack resource,double kelvin,FluidAction action) { return fillAt(resource,kelvin,Double.NaN,action); }
    /** As above, for a stream that is partly through boiling or condensing; an empty tank keeps the gas share. */
    public int fillAt(FluidStack resource,double kelvin,double quality,FluidAction action) {
        entryKelvin=kelvin;entryQuality=quality;
        try { return fill(resource,action); } finally { entryKelvin=Double.NaN;entryQuality=Double.NaN; }
    }
    /**
     * Moves up to {@code amount} to another tank with its real temperature and gas share, so a part-way boiling or condensing
     * content keeps its latent heat. Returns what the other tank took.
     */
    public int transferTo(ThermalFluidTank target,int amount) {
        double kelvin=kelvin(), quality=heat.quality(fluid);
        var offered=drain(amount,FluidAction.SIMULATE);
        int fit=offered.isEmpty() ? 0 : target.fillAt(offered,kelvin,quality,FluidAction.SIMULATE);
        if(fit<=0)return 0;
        return target.fillAt(drain(fit,FluidAction.EXECUTE),kelvin,quality,FluidAction.EXECUTE);
    }
    @Override public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        // Emptying the tank from outside (a rebuilt structure, a cleared machine) leaves no heat behind.
        if(stack.isEmpty())heat.forget();
    }
    @Override public CompoundTag writeToNBT(HolderLookup.Provider lookup,CompoundTag tag) {
        super.writeToNBT(lookup,tag);
        heat.write(tag);
        return tag;
    }
    @Override public net.neoforged.neoforge.fluids.capability.templates.FluidTank readFromNBT(HolderLookup.Provider lookup,CompoundTag tag) {
        super.readFromNBT(lookup,tag);
        heat.read(tag);
        return this;
    }
    @Override public FluidStack drain(int maxDrain,FluidAction action) {
        if(action.simulate() || fluid.isEmpty())return super.drain(maxDrain,action);
        var before=fluid.copy();
        var out=super.drain(maxDrain,action);
        if(!out.isEmpty() && heat.leave(before,out,fluid))onContentsChanged();
        return out;
    }
    public int volumeCapacity(){return capacity;}
    /** How much gas at 1 bar the tank holds when it holds gas. */
    public int gasCapacity(){return (int)Math.min(Integer.MAX_VALUE,(long)capacity*storageRatio());}
    /** How many mB of gas at 1 bar fill one mB of volume; a tank holds gas up to its pressure rating. */
    protected int storageRatio(){return Grade.Pressure.PRESSURISED.storageRatio();}
    protected int storageRatio(FluidStack stack) {
        int rating = storageRatio();
        return Compression.isAir(stack) ? Math.min(rating, Compression.airVolumeRatio(stack)) : rating;
    }
    protected boolean storedGas(FluidStack stack){return !stack.isEmpty() && (net.createplant.mixture.MixtureHelper.isMixture(stack) ? net.createplant.mixture.MixtureHelper.gas(stack) : net.createplant.transport.FluidHazards.of(stack).gas());}
    @Override public int getCapacity(){return storedGas(fluid) ? (int)Math.min(Integer.MAX_VALUE,(long)capacity*storageRatio(fluid)) : capacity;}
    @Override public int getSpace(){return Math.max(0,getCapacity()-getFluidAmount());}
    @Override public ThermalFluidTank setCapacity(int next){var kept=fluid.copy();super.setCapacity(next);if(storedGas(kept))fluid=kept;return this;}
    @Override public int fill(FluidStack resource,FluidAction action) {
        if (mixesGases() && storedGas(resource) && (fluid.isEmpty() || storedGas(fluid)) && (net.createplant.mixture.MixtureHelper.isMixture(resource)
            || net.createplant.mixture.MixtureHelper.isMixture(fluid) || !fluid.isEmpty() && !FluidThermal.mixable(fluid,resource)))
            return fillGasMixture(resource,action);
        if (net.createplant.mixture.MixtureHelper.isMixture(resource) || net.createplant.mixture.MixtureHelper.isMixture(fluid))
            return net.createplant.mixture.MixtureTransfer.fill(this, resource, action, false);
        if(resource.isEmpty() || !FluidThermal.valid(resource) || !isFluidValid(resource)
            || !fluid.isEmpty() && !FluidThermal.mixable(fluid,resource))return 0;
        int accepted;
        FluidStack result;
        if(fluid.isEmpty()) {
            accepted=Math.min(resource.getAmount(),capacityFor(resource));
            if(accepted<=0)return 0;
            result=resource.copyWithAmount(accepted);
        } else {
            accepted=mixAmount(resource);
            if(accepted<=0)return 0;
            result=mixed(resource,accepted);
        }
        if(action.execute()) {
            boolean wasEmpty=fluid.isEmpty();
            double kelvin=heat.enter(fluid,resource,accepted,entryKelvin,true);
            fluid=result;
            int level=FluidState.levelFor(fluid,kelvin,FluidState.pressure(fluid),FluidState.gas(fluid));
            if(level!=FluidState.temperature(fluid))FluidState.setTemperature(fluid,level);
            heat.set(fluid,kelvin,wasEmpty ? entryQuality : Double.NaN);
            net.createplant.nuclear.RadiationSources.stamp(fluid);
            onContentsChanged();
        }
        return accepted;
    }

    public int capacityFor(FluidStack result) {
        return !storedGas(result) ? capacity : (int)Math.clamp((long)capacity*storageRatio(result)-sharedGas(),0,Integer.MAX_VALUE);
    }

    private int mixedState(FluidStack resource,int amount) {
        int temperature=FluidState.temperatureLevel(heat.enter(fluid,resource,amount,entryKelvin,false));
        int pressure=FluidState.average(FluidState.pressure(fluid),getFluidAmount(),FluidState.pressure(resource),amount);
        return temperature*17+pressure;
    }

    // Over each interval with the same rounded levels, capacity and conversion units are fixed.
    private int mixAmount(FluidStack resource) {
        int maximum=Math.min(resource.getAmount(),Integer.MAX_VALUE-getFluidAmount());
        int ratio=PhaseChange.ratio(fluid);
        int storedUnit=storedGas(fluid) ? 1 : ratio;
        int addedUnit=storedGas(resource) ? 1 : ratio;
        long stored=(long)getFluidAmount()*storedUnit;
        while(maximum>0) {
            int state=mixedState(resource,maximum);
            var result=FluidState.with(fluid,state/17,state%17);
            int resultUnit=storedGas(result) ? 1 : ratio;
            int amount=(int)Math.clamp(((long)capacityFor(result)*resultUnit-stored)/addedUnit,0,maximum);
            long remainder=(stored+(long)amount*addedUnit)%resultUnit;
            if(remainder!=0)amount=addedUnit==1 ? amount-(int)remainder : 0;
            if(amount>0 && mixedState(resource,amount)==state)return amount;
            int low=1,high=maximum;
            while(low<high) {
                int middle=(int)((low+(long)high)/2);
                if(mixedState(resource,middle)==state)high=middle;
                else low=middle+1;
            }
            maximum=low-1;
        }
        return 0;
    }

    private FluidStack mixed(FluidStack resource,int amount) {
        var combined=FluidThermal.mix(fluid,resource,amount);
        boolean before=storedGas(fluid), incoming=storedGas(resource), after=storedGas(combined);
        if(before==incoming && incoming==after)return combined;
        int ratio=PhaseChange.ratio(fluid);
        long units=(long)getFluidAmount()*(before ? 1 : ratio)+(long)amount*(incoming ? 1 : ratio);
        combined.setAmount((int)(units/(after ? 1 : ratio)));
        return combined;
    }
    /** Gas held elsewhere in the same volume, such as a working vessel's process buffers. */
    protected long sharedGas() { return 0; }
    /** Storage tanks mix gases; a machine's own buffers keep one gas each. */
    protected boolean mixesGases() { return false; }
    /** Different gases share the tank as one mixture that keeps each component's volume. */
    private int fillGasMixture(FluidStack resource,FluidAction action) {
        if (!isFluidValid(resource) || !net.createplant.mixture.MixtureHelper.isMixableGas(resource)) return 0;
        long room=(long)capacity*storageRatio(fluid.isEmpty() ? resource : fluid)-getFluidAmount()-sharedGas();
        int accepted=(int)Math.max(0,Math.min(resource.getAmount(),Math.min(Integer.MAX_VALUE,room)));
        if (accepted==0) return 0;
        FluidStack merged;
        try { merged=net.createplant.mixture.MixtureHelper.merge(fluid,net.createplant.mixture.MixtureHelper.portion(resource,accepted),true); }
        catch (IllegalArgumentException error) { return 0; }
        if (action.execute()) {
            double kelvin=heat.enter(fluid,resource,accepted,entryKelvin,true);
            fluid=merged;
            int level=FluidState.temperatureLevel(kelvin);
            if(!net.createplant.mixture.MixtureHelper.isMixture(fluid) && level!=FluidState.temperature(fluid))FluidState.setTemperature(fluid,level);
            heat.set(fluid,kelvin);
            onContentsChanged();
        }
        return accepted;
    }
    @Override public FluidStack drain(FluidStack request,FluidAction action) {
        if (request.isEmpty() || !net.createplant.mixture.MixtureTransfer.matches(fluid,request)) return FluidStack.EMPTY;
        return drain(request.getAmount(),action);
    }



}
