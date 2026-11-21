package net.createplant.thermal;

import java.util.function.*;
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
    @Override public boolean isFluidValid(FluidStack stack){var grade=TankGrade.of(owner);return super.isFluidValid(stack) && (grade==null || grade.storable(stack));}
    public boolean gasStored(){return storedGas(fluid);}
    public int gasVolume(){return gasStored() ? (int)Math.min(volumeCapacity(),((long)getFluidAmount()+storageRatio(fluid)-1)/storageRatio(fluid)) : getFluidAmount();}
    public boolean absorbGas(FluidStack added){if(!storedGas(added) || !isFluidValid(added) || !fluid.isEmpty() && !FluidThermal.compatible(fluid,added))return false;fluid=fluid.isEmpty() ? added.copy() : FluidThermal.mix(fluid,added,added.getAmount());onContentsChanged();return true;}
    @Override public int fill(FluidStack incoming,FluidAction action){
        if (owner instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel && !vessel.hasInternals()
            && net.createplant.mixture.MixtureHelper.isMixableLiquid(incoming) && !fluid.isEmpty()
            && net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid) && !FluidThermal.mixable(fluid, incoming))
            return net.createplant.mixture.MixtureTransfer.fill(this, incoming, action, true);
        return super.fill(incoming,action);
    }
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback){return port(part,side,fallback,()->true,()->true);}
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback,BooleanSupplier available){return port(part,side,fallback,available,()->true);}
    public static IFluidHandler port(SmartBlockEntity part,Direction side,IFluidHandler fallback,BooleanSupplier available,BooleanSupplier filling){
        return new IFluidHandler(){
            private boolean live(){return !part.isRemoved() && part.getLevel()!=null && available.getAsBoolean();}
            private GradeFluidTank tank(){if(!live() || !(part instanceof IMultiBlockEntityContainer.Fluid vessel))return null;var owner=vessel.getControllerBE();if(owner instanceof FluidTankBlockEntity tank && tank.boiler.isActive())return null;return owner instanceof IMultiBlockEntityContainer.Fluid root && root.getTank(0) instanceof GradeFluidTank tank ? tank : null;}
            public int getTanks(){return 1;}
            public FluidStack getFluidInTank(int index){if(!live() || index!=0)return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.getFluidInTank(index) : tank.getFluid().copy();}
            public int getTankCapacity(int index){if(!live() || index!=0)return 0;var tank=tank();return tank==null ? fallback.getTankCapacity(index) : tank.getCapacity();}
            public boolean isFluidValid(int index,FluidStack stack){if(!live() || index!=0 || !filling.getAsBoolean())return false;var tank=tank();return tank==null ? fallback.isFluidValid(index,stack) : tank.isFluidValid(stack);}
            public int fill(FluidStack stack,FluidAction action){if(!live() || !filling.getAsBoolean())return 0;var tank=tank();return tank==null ? fallback.fill(stack,action) : tank.fill(stack,action);}
            public FluidStack drain(int count,FluidAction action){if(!live())return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.drain(count,action) : tank.drain(count,action);}
            public FluidStack drain(FluidStack request,FluidAction action){if(!live() || request.isEmpty())return FluidStack.EMPTY;var tank=tank();return tank==null ? fallback.drain(request,action) : tank.drain(request,action);}
        };
    }
}
