package net.createplant.mixin;

import java.util.List;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createplant.basin.BasinGases;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Open Create basins keep their native storage and reject gas through standard pipes. */
@Mixin(BasinBlockEntity.class)
public class BasinBlockEntityMixin {
    @Shadow protected IFluidHandler fluidCapability;
    @Inject(method = "addBehaviours", at = @At("TAIL"))
    private void createPlant$liquidOnly(List<BlockEntityBehaviour> behaviours, CallbackInfo callback) {
        var wrapped = fluidCapability;
        fluidCapability = new IFluidHandler() {
            @Override public int getTanks() { return wrapped.getTanks(); }
            @Override public FluidStack getFluidInTank(int tank) { return wrapped.getFluidInTank(tank); }
            @Override public int getTankCapacity(int tank) { return wrapped.getTankCapacity(tank); }
            @Override public boolean isFluidValid(int tank, FluidStack fluid) { return !BasinGases.gas(fluid) && wrapped.isFluidValid(tank, fluid); }
            @Override public int fill(FluidStack fluid, FluidAction action) { return BasinGases.gas(fluid) ? 0 : wrapped.fill(fluid, action); }
            @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return wrapped.drain(fluid, action); }
            @Override public FluidStack drain(int amount, FluidAction action) { return wrapped.drain(amount, action); }
        };
    }
}
