package net.createplant.block.entity;

import net.createplant.registry.CEFluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class GasOnlyFluidHandler implements IFluidHandler {
    private final IFluidHandler wrapped;

    public GasOnlyFluidHandler(IFluidHandler wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public int getTanks() {
        return wrapped.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return wrapped.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return wrapped.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return isGas(stack) && wrapped.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return isGas(resource) ? wrapped.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return isGas(resource) ? wrapped.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return wrapped.drain(maxDrain, action);
    }

    public static boolean isGas(FluidStack stack) {
        return !stack.isEmpty()
            && (stack.getFluid().getFluidType() == CEFluids.HYDROGEN_GAS_TYPE.get()
                || stack.getFluid().getFluidType() == CEFluids.OXYGEN_GAS_TYPE.get()
                || stack.getFluid().getFluidType() == CEFluids.NITROGEN_GAS_TYPE.get()
                || stack.getFluid().getFluidType() == CEFluids.AMMONIA_GAS_TYPE.get()
                || stack.getFluid().getFluidType() == net.createplant.registry.CEOstwaldFluids.NITRIC_OXIDE_TYPE.get()
                || stack.getFluid().getFluidType() == net.createplant.registry.CEOstwaldFluids.NITROGEN_DIOXIDE_TYPE.get()
                || stack.getFluid().getFluidType() == net.createplant.registry.CEPetroFluids.ETHYLENE_TYPE.get()
                || stack.getFluid().getFluidType() == net.createplant.registry.CEPetroFluids.PROPYLENE_TYPE.get());
    }
}
