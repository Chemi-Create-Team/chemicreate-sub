package net.createplant.thermal;

import java.util.function.Consumer;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;

/** Opt-in tank: preserves all other component distinctions and the native callback/save contract. */
public class ThermalFluidTank extends SmartFluidTank {
    public ThermalFluidTank(int capacity,Consumer<FluidStack> changed) { super(capacity,changed); }
    public int volumeCapacity(){return capacity;}
    /** How many mB of gas at 1 bar fill one mB of volume; a tank holds gas up to its pressure rating. */
    protected int storageRatio(){return Grade.Pressure.PRESSURISED.storageRatio();}
    protected int storageRatio(FluidStack stack) {
        int rating = storageRatio();
        return Compression.isAir(stack) ? Math.min(rating, Compression.airVolumeRatio(stack)) : rating;
    }
    protected boolean storedGas(FluidStack stack){return !stack.isEmpty() && net.createplant.transport.FluidHazards.of(stack).gas();}
    @Override public int getCapacity(){return storedGas(fluid) ? (int)Math.min(Integer.MAX_VALUE,(long)capacity*storageRatio(fluid)) : capacity;}
    @Override public int getSpace(){return Math.max(0,getCapacity()-getFluidAmount());}
    @Override public ThermalFluidTank setCapacity(int next){var kept=fluid.copy();super.setCapacity(next);if(storedGas(kept))fluid=kept;return this;}
    @Override public int fill(FluidStack resource,FluidAction action) {
        if (storedGas(resource) && (fluid.isEmpty() || storedGas(fluid)) && (net.createplant.mixture.MixtureHelper.isMixture(resource)
            || net.createplant.mixture.MixtureHelper.isMixture(fluid) || !fluid.isEmpty() && !FluidThermal.mixable(fluid,resource)))
            return fillGasMixture(resource,action);
        if (net.createplant.mixture.MixtureHelper.isMixture(resource) || net.createplant.mixture.MixtureHelper.isMixture(fluid))
            return net.createplant.mixture.MixtureTransfer.fill(this, resource, action, false);
        if(storedGas(resource)){
            if(!FluidThermal.valid(resource) || !isFluidValid(resource) || !fluid.isEmpty() && !FluidThermal.mixable(fluid,resource))return 0;
            int n=(int)Math.max(0,Math.min(resource.getAmount(),Math.min(Integer.MAX_VALUE,(long)capacity*storageRatio(resource))-getFluidAmount()));
            if(n>0 && action.execute()){var portion=FluidThermal.at(resource.copyWithAmount(n),FluidThermal.kelvin(resource));fluid=fluid.isEmpty() ? portion : FluidThermal.mix(fluid,portion,n);net.createplant.nuclear.RadiationSources.stamp(fluid);onContentsChanged();}return n;
        }
        if (resource.isEmpty() || !FluidThermal.valid(resource) || !isFluidValid(resource) || !fluid.isEmpty() && !FluidThermal.mixable(fluid,resource)) return 0;
        int accepted=Math.max(0,Math.min(getSpace(),resource.getAmount()));
        if (accepted>0 && action.execute()) {
            fluid=fluid.isEmpty() ? resource.copyWithAmount(accepted) : FluidThermal.mix(fluid,resource,accepted);
            net.createplant.nuclear.RadiationSources.stamp(fluid);
            onContentsChanged();
        }
        return accepted;
    }
    /** Different gases share the tank as one mixture that keeps each component's volume. */
    private int fillGasMixture(FluidStack resource,FluidAction action) {
        if (!isFluidValid(resource) || !net.createplant.mixture.MixtureHelper.isMixableGas(resource)) return 0;
        long room=(long)capacity*storageRatio(fluid.isEmpty() ? resource : fluid)-getFluidAmount();
        int accepted=(int)Math.max(0,Math.min(resource.getAmount(),Math.min(Integer.MAX_VALUE,room)));
        if (accepted==0) return 0;
        FluidStack merged;
        try { merged=net.createplant.mixture.MixtureHelper.merge(fluid,net.createplant.mixture.MixtureHelper.portion(resource,accepted),true); }
        catch (IllegalArgumentException error) { return 0; }
        if (action.execute()) { fluid=merged;onContentsChanged(); }
        return accepted;
    }
    @Override public FluidStack drain(FluidStack request,FluidAction action) {
        if (request.isEmpty() || !net.createplant.mixture.MixtureTransfer.matches(fluid,request)) return FluidStack.EMPTY;
        return drain(request.getAmount(),action);
    }



}
