package net.createplant.mixture;

import net.createplant.mixin.FluidTankAccess;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Shared by opt-in mixers and native tanks already carrying a mixture. */
public final class MixtureTransfer {
    private MixtureTransfer() { }
    public static boolean matches(FluidStack stored, FluidStack request) {
        if (MixtureHelper.isMixture(stored) && MixtureHelper.isMixture(request)) return true;
        return FluidStack.isSameFluidSameComponents(stored, request);
    }
    public static int fill(FluidTank tank, FluidStack resource, FluidAction action, boolean unwrap) {
        if (!MixtureHelper.isMixableLiquid(resource) || !tank.isFluidValid(resource)) return 0;
        var stored = tank.getFluid();if (!stored.isEmpty() && !MixtureHelper.isMixableLiquid(stored)) return 0;
        int accepted = Math.min(Math.max(0, tank.getCapacity() - stored.getAmount()), resource.getAmount());if (accepted == 0) return 0;
        var portion = MixtureHelper.portion(resource, accepted);FluidStack result;
        try { result = MixtureHelper.merge(stored, portion, unwrap); }
        catch (IllegalArgumentException error) { return 0; }
        if (action.execute()) {
            var access = (FluidTankAccess) tank;access.createPlant$setFluid(result);access.createPlant$contentsChanged();
        }
        return accepted;
    }
}
