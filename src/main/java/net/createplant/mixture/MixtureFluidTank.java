package net.createplant.mixture;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;

/** A liquid-only, opt-in mixed inventory using the standard tank save/sync contract. */
public class MixtureFluidTank extends SmartFluidTank {
    private final boolean unwrapPureOutput;
    public MixtureFluidTank(int capacity, boolean unwrapPureOutput, Runnable onContentsChanged) {
        super(capacity, stack -> onContentsChanged.run());
        if (capacity < 0) throw new IllegalArgumentException("Negative mixture capacity");
        this.unwrapPureOutput = unwrapPureOutput;
    }
    @Override public boolean isFluidValid(FluidStack resource) { return MixtureHelper.isMixableLiquid(resource); }
    @Override public int fill(FluidStack resource, FluidAction action) {
        return MixtureTransfer.fill(this, resource, action, unwrapPureOutput);
    }
    @Override public FluidStack drain(int maxDrain, FluidAction action) {
        int amount = Math.min(Math.max(0, maxDrain), getFluidAmount());if (amount == 0) return FluidStack.EMPTY;
        var split = MixtureHelper.extractContents(fluid).split(amount);var result = MixtureHelper.createStack(split.extracted(), MixtureHelper.data(fluid), unwrapPureOutput);
        if (action.execute()) { fluid = MixtureHelper.createStack(split.remaining(), MixtureHelper.data(fluid), unwrapPureOutput);onContentsChanged(); }
        return result;
    }
    @Override public FluidStack drain(FluidStack request, FluidAction action) {
        if (request.isEmpty() || !MixtureTransfer.matches(fluid, request)) return FluidStack.EMPTY;
        return drain(request.getAmount(), action);
    }
}
