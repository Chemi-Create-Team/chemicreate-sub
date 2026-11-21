package net.createplant.mixin;

import java.util.function.Consumer;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity.CreativeSmartFluidTank;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.mixture.MixtureHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep component-free JEI mixture samples out of the creative source and sink. */
@Mixin(value = CreativeSmartFluidTank.class, remap = false)
public abstract class CreativeTankMixtureMixin extends SmartFluidTank {
    protected CreativeTankMixtureMixin(int capacity, Consumer<FluidStack> changed) { super(capacity, changed); }
    @Unique private static boolean createPlant$validSample(FluidStack fluid) { return !MixtureHelper.isMixture(fluid) || MixtureHelper.isMixableLiquid(fluid) || MixtureHelper.isMixableGas(fluid); }
    @Override public boolean isFluidValid(FluidStack fluid) { return createPlant$validSample(fluid) && super.isFluidValid(fluid); }
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createPlant$rejectInvalidFill(FluidStack fluid, FluidAction action, CallbackInfoReturnable<Integer> cir) { if (!createPlant$validSample(fluid)) cir.setReturnValue(0); }
    @Inject(method = "setContainedFluid", at = @At("HEAD"), cancellable = true)
    private void createPlant$rejectInvalidSelection(FluidStack fluid, CallbackInfo ci) { if (!createPlant$validSample(fluid)) ci.cancel(); }
}
