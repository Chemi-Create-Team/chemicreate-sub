package net.createplant.mixin;

import java.util.function.Supplier;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidThermal;
import net.createplant.transport.WorldLiquidDrainer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HosePulleyFluidHandler.class, remap = false)
public class WorldLiquidHoseMixin {
    @Shadow private SmartFluidTank internalTank;
    @Shadow private FluidFillingBehaviour filler;
    @Shadow private FluidDrainingBehaviour drainer;
    @Shadow private Supplier<BlockPos> rootPosGetter;
    @Shadow private Supplier<Boolean> predicate;

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createPlant$fillProcessLiquid(FluidStack resource, FluidAction action, CallbackInfoReturnable<Integer> cir) {
        FluidStack held = internalTank.getFluid();
        if (!CEWorldFluids.isLiquid(resource.getFluid()) && !CEWorldFluids.isLiquid(held.getFluid())) return;
        if (resource.isEmpty() || !FluidThermal.valid(resource) || !held.isEmpty() && !FluidThermal.compatible(held, resource)) {
            cir.setReturnValue(0);
            return;
        }
        int heldAmount = held.getAmount();
        boolean deposited = heldAmount + resource.getAmount() >= 1000 && predicate.get()
            && filler.tryDeposit(resource.getFluid(), rootPosGetter.get(), action.simulate());
        int intoSource = deposited ? Math.max(0, 1000 - heldAmount) : 0;
        int retained = deposited ? Math.max(0, heldAmount - 1000) : heldAmount;
        int intoBuffer = Math.min(resource.getAmount() - intoSource, internalTank.getCapacity() - retained);
        if (action.execute()) {
            FluidStack left = retained == 0 ? FluidStack.EMPTY : held.copyWithAmount(retained);
            internalTank.setFluid(intoBuffer <= 0 ? left : FluidThermal.add(left, resource, intoBuffer));
            if (deposited) drainer.counterpartActed();
        }
        cir.setReturnValue(intoSource + Math.max(0, intoBuffer));
    }

    @Inject(method = "drainInternal", at = @At("HEAD"), cancellable = true)
    private void createPlant$drainProcessLiquid(int amount, FluidStack filter, FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        FluidStack held = internalTank.getFluid();
        if (!CEWorldFluids.isLiquid(held.getFluid()) && !((WorldLiquidDrainer) drainer).createPlant$processLiquid()) return;
        FluidStack source = held.isEmpty() ? drainer.getDrainableFluid(rootPosGetter.get()) : FluidStack.EMPTY;
        FluidStack candidate = held.isEmpty() ? source : held;
        if (amount <= 0 || filter != null && !FluidThermal.compatible(candidate, filter)) {
            cir.setReturnValue(FluidStack.EMPTY);
            return;
        }
        if (!held.isEmpty()) {
            cir.setReturnValue(internalTank.drain(amount, action));
            return;
        }
        if (!predicate.get() || source.isEmpty() || !drainer.pullNext(rootPosGetter.get(), action.simulate())) {
            cir.setReturnValue(FluidStack.EMPTY);
            return;
        }
        int drained = Math.min(amount, source.getAmount());
        if (action.execute()) {
            internalTank.setFluid(drained == source.getAmount() ? FluidStack.EMPTY : source.copyWithAmount(source.getAmount() - drained));
            filler.counterpartActed();
        }
        cir.setReturnValue(source.copyWithAmount(drained));
    }
}
