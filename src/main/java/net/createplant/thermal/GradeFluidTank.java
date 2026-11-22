package net.createplant.thermal;

import java.util.function.*;
import net.createplant.mixture.MixtureHelper;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Rated native tank inventory, including Create's gas merge/split bookkeeping. */
public class GradeFluidTank extends ThermalFluidTank {
    private final SmartBlockEntity owner;
    public GradeFluidTank(int capacity,Consumer<FluidStack> changed,SmartBlockEntity owner){super(capacity,changed);this.owner=owner;}
    @Override protected int storageRatio(){var grade=TankGrade.of(owner);return grade==null ? 16 : grade.gasRatio();}
    @Override protected long sharedGas(){return owner instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel ? vessel.gasOutsideStorage() : 0;}
    @Override protected boolean mixesGases(){return owner instanceof net.createplant.block.entity.LiddedVesselBlockEntity || TankGrade.of(owner)!=null;}
    @Override public boolean isFluidValid(FluidStack stack){var grade=TankGrade.of(owner);return super.isFluidValid(stack) && (grade==null || grade.storable(stack));}
    public boolean gasStored(){return storedGas(fluid);}
    public int gasVolume(){return gasStored() ? (int)Math.min(volumeCapacity(),((long)getFluidAmount()+storageRatio(fluid)-1)/storageRatio(fluid)) : getFluidAmount();}
    public boolean absorbGas(FluidStack added) {
        if (!storedGas(added) || !isFluidValid(added)) return false;
        FluidStack combined;
        if (fluid.isEmpty()) combined = added.copy();
        else if (MixtureHelper.isMixture(fluid) || MixtureHelper.isMixture(added)) {
            try { combined = MixtureHelper.merge(fluid, added, true); }
            catch (IllegalArgumentException error) { return false; }
        } else if (FluidThermal.compatible(fluid, added)) combined = FluidThermal.mix(fluid, added, added.getAmount());
        else return false;
        setFluid(combined);
        return true;
    }
    @Override public int fill(FluidStack incoming,FluidAction action){
        if (owner instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel && !vessel.stirred()
            && net.createplant.mixture.MixtureHelper.isMixableLiquid(incoming) && !fluid.isEmpty()
            && net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid) && !FluidThermal.mixable(fluid, incoming))
            return net.createplant.mixture.MixtureTransfer.fill(this, incoming, action, true);
        return super.fill(incoming,action);
    }
    private boolean layered(Direction side) { return side != null && side.getAxis().isVertical() && MixtureHelper.composite(fluid); }
    public FluidStack layer(Direction side) {
        if (!layered(side)) return fluid.copy();
        var layers = TankLayers.ordered(java.util.List.of(fluid));
        return side == Direction.UP ? layers.getLast() : layers.getFirst();
    }
    public FluidStack drainLayer(Direction side, FluidStack request, int amount, FluidAction action) {
        if (!layered(side)) return request.isEmpty() ? drain(amount, action) : drain(request, action);
        var layer = layer(side);
        if (amount <= 0 || !request.isEmpty() && !FluidThermal.compatible(layer, request)) return FluidStack.EMPTY;
        return drainComponent(layer, amount, action);
    }
    public FluidStack drainComponent(FluidStack layer, int amount, FluidAction action) {
        var removal = TankLayers.remove(fluid, layer, amount);
        if (action.execute() && !removal.sample().isEmpty()) setFluid(removal.remaining());
        return removal.sample();
    }
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback){return port(part,side,fallback,()->true,()->true);}
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback,BooleanSupplier available){return port(part,side,fallback,available,()->true);}
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback,BooleanSupplier available,BooleanSupplier filling){
        return new IFluidHandler(){
            private boolean live(){return !part.isRemoved() && part.getLevel()!=null && available.getAsBoolean();}
            private GradeFluidTank tank(){if(!live() || !(part instanceof IMultiBlockEntityContainer.Fluid vessel))return null;var owner=vessel.getControllerBE();if(owner instanceof FluidTankBlockEntity tank && tank.boiler.isActive())return null;return owner instanceof IMultiBlockEntityContainer.Fluid root && root.getTank(0) instanceof GradeFluidTank tank ? tank : null;}
            public int getTanks(){return 1;}
            public FluidStack getFluidInTank(int index){if(!live() || index!=0)return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.getFluidInTank(index) : tank.layer(side);}
            public int getTankCapacity(int index){if(!live() || index!=0)return 0;var tank=tank();return tank==null ? fallback.getTankCapacity(index) : tank.getCapacity();}
            public boolean isFluidValid(int index,FluidStack stack){if(!live() || index!=0 || !filling.getAsBoolean())return false;var tank=tank();return tank==null ? fallback.isFluidValid(index,stack) : tank.isFluidValid(stack);}
            public int fill(FluidStack stack,FluidAction action){if(!live() || !filling.getAsBoolean())return 0;var tank=tank();return tank==null ? fallback.fill(stack,action) : tank.fill(stack,action);}
            public FluidStack drain(int count,FluidAction action){if(!live())return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.drain(count,action) : tank.drainLayer(side,FluidStack.EMPTY,count,action);}
            public FluidStack drain(FluidStack request,FluidAction action){if(!live() || request.isEmpty())return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.drain(request,action) : tank.drainLayer(side,request,request.getAmount(),action);}
        };
    }
}
