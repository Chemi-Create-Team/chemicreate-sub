package net.createplant.mixin;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FluidTankBlockEntity.class)
public interface FluidTankBlockEntityAccessor {
    @Invoker("refreshCapability")
    void createPlant$refreshCapability();
}
