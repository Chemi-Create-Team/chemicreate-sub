package net.createplant.thermal;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Native gas-tank animation and save keys, with temperature-aware mixing. */
public final class ThermalTankBehaviour extends SmartFluidTankBehaviour {
    public ThermalTankBehaviour(SmartBlockEntity owner,int capacity) {
        super(TYPE,owner,1,capacity,false);
        tanks[0]=new ThermalSegment(capacity);
        capability=new InternalFluidHandler(new IFluidHandler[]{getPrimaryHandler()},false);
    }
    private class ThermalSegment extends TankSegment {
        ThermalSegment(int capacity) { super(capacity);tank=new ThermalFluidTank(capacity,stack->onFluidStackChanged()); }
    }
}
