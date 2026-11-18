package net.createplant.block.entity;

import net.createplant.thermal.FluidThermal;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/** Every casing port reaches the same finite bank of rotors, without copying its inventories. */
final class TurbineUnitPort implements IFluidHandler {
    private final SteamTurbineBlockEntity owner;
    private final boolean liquid;

    TurbineUnitPort(SteamTurbineBlockEntity owner, boolean liquid) {
        this.owner = owner;
        this.liquid = liquid;
    }

    private List<IFluidHandler> handlers() {
        var world = owner.getLevel();
        var unit = owner.unitLayout();
        if (owner.isRemoved() || world == null || unit == null) return List.of();
        var sections = owner.unitAssembly();
        var result = new ArrayList<IFluidHandler>(sections.size());
        for (var section : sections) {
            BlockPos pos = section.origin();
            if (!world.hasChunkAt(pos)
                    || !(world.getBlockEntity(pos) instanceof SteamTurbineBlockEntity rotor))
                return List.of();
            result.add(liquid ? rotor.localLiquidPort() : rotor.localSteamPort());
        }
        return result;
    }

    @Override
    public int getTanks() {
        return liquid ? 1 : 2;
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank < 0 || tank >= getTanks()
                ? 0
                : handlers().stream().mapToInt(h -> h.getTankCapacity(tank)).sum();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= getTanks()) return FluidStack.EMPTY;
        FluidStack total = FluidStack.EMPTY;
        for (var handler : handlers()) {
            var stack = handler.getFluidInTank(tank);
            if (stack.isEmpty() || !total.isEmpty() && !FluidThermal.compatible(total, stack))
                continue;
            total = FluidThermal.add(total, stack, stack.getAmount());
        }
        return total;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !liquid
                && tank == 0
                && handlers().stream().anyMatch(h -> h.isFluidValid(tank, stack));
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
        if (liquid || stack.isEmpty()) return 0;
        int accepted = 0;
        for (var handler : handlers()) {
            accepted += handler.fill(stack.copyWithAmount(stack.getAmount() - accepted), action);
            if (accepted >= stack.getAmount()) break;
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack request, FluidAction action) {
        if (request.isEmpty() || !FluidThermal.compatible(request, getFluidInTank(liquid ? 0 : 1)))
            return FluidStack.EMPTY;
        return drain(request.getAmount(), action);
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0) return FluidStack.EMPTY;
        FluidStack total = FluidStack.EMPTY;
        for (var handler : handlers()) {
            var preview = handler.drain(amount - total.getAmount(), FluidAction.SIMULATE);
            if (preview.isEmpty() || !total.isEmpty() && !FluidThermal.compatible(total, preview))
                continue;
            var moved = action.execute() ? handler.drain(preview.getAmount(), action) : preview;
            if (!moved.isEmpty()) total = FluidThermal.add(total, moved, moved.getAmount());
            if (total.getAmount() >= amount) break;
        }
        return total;
    }
}
