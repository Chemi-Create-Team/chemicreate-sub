package net.createplant.mixin;

import java.util.function.Consumer;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The creative liquid source must use the same phase restriction as liquid storage, including direct bucket selection. */
@Mixin(value = CreativeSmartFluidTank.class, remap = false)
public abstract class CreativeLiquidTankMixin extends SmartFluidTank {
    protected CreativeLiquidTankMixin(int capacity, Consumer<FluidStack> changed) { super(capacity, changed); }
    @Unique private static boolean createPlant$liquid(FluidStack fluid) { return fluid.isEmpty() || net.createplant.mixture.MixtureHelper.isMixableLiquid(fluid); }
    @Override public boolean isFluidValid(FluidStack fluid) { return createPlant$liquid(fluid) && super.isFluidValid(fluid); }
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createPlant$rejectGas(FluidStack fluid, FluidAction action, CallbackInfoReturnable<Integer> cir) { if (!createPlant$liquid(fluid)) cir.setReturnValue(0); }
    @Inject(method = "setContainedFluid", at = @At("HEAD"), cancellable = true)
    private void createPlant$selectLiquid(FluidStack fluid, CallbackInfo ci) { if (!createPlant$liquid(fluid)) ci.cancel(); }
}
