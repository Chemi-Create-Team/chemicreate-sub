package net.createplant.nuclear;

import net.createplant.block.entity.FuelChannelBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;

public final class RadiationContents {
    private RadiationContents() {}

    public static BlockEntity owner(BlockEntity be) {
        if (be instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel)
            return vessel.getControllerBE();
        if (be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)
            return tank.getControllerBE();
        if (be instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault)
            return vault.getControllerBE();
        return be;
    }

    public static RadiationPhysics.Spectrum sample(ServerLevel level, BlockEntity be) {
        if (be == null || be.isRemoved() || be instanceof FuelChannelBlockEntity)
            return RadiationPhysics.Spectrum.ZERO;
        long now = RadiationSources.now(level);
        var result = RadiationPhysics.Spectrum.ZERO;
        if (be instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin) {
            if (basin.getTanks() == null || basin.getInputInventory() == null) return result;
            for (var inventory :
                    java.util.List.of(basin.getInputInventory(), basin.getOutputInventory()))
                for (int slot = 0; slot < inventory.getSlots(); slot++)
                    result =
                            result.plus(RadiationSources.item(inventory.getStackInSlot(slot), now));
            for (var tank :
                    java.util.List.of(
                            basin.getTanks().getFirst().getCapability(),
                            basin.getTanks().getSecond().getCapability()))
                for (int slot = 0; slot < tank.getTanks(); slot++)
                    result = result.plus(RadiationSources.fluid(tank.getFluidInTank(slot), now));
            return result;
        }
        if (be instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                var stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    var components = stack.getComponentsPatch();
                    RadiationSources.stamp(stack, level);
                    if (!components.equals(stack.getComponentsPatch())) be.setChanged();
                }
                result = result.plus(RadiationSources.item(stack, now));
            }
        } else {
            var handlers = new HashSet<IItemHandler>();
            IItemHandler all =
                    level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
            if (all != null) handlers.add(all);
            else
                for (Direction side : Direction.values()) {
                    var h =
                            level.getCapability(
                                    Capabilities.ItemHandler.BLOCK, be.getBlockPos(), side);
                    if (h != null) handlers.add(h);
                }
            var seen = new HashSet<String>();
            for (var handler : handlers)
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    var stack = handler.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    // Side-specific wrappers often expose the same slots. Include the slot index so
                    // equal stacks in separate slots still count.
                    if (handlers.size() > 1) {
                        String key = slot + ":" + stack.save(level.registryAccess());
                        if (!seen.add(key)) continue;
                    }
                    if (handler
                            instanceof
                            net.neoforged.neoforge.items.IItemHandlerModifiable mutable) {
                        var components = stack.getComponentsPatch();
                        var stamped = RadiationSources.stamp(stack.copy(), level);
                        if (!components.equals(stamped.getComponentsPatch())) {
                            mutable.setStackInSlot(slot, stamped);
                            be.setChanged();
                            stack = stamped;
                        }
                    }
                    result = result.plus(RadiationSources.item(stack, now));
                }
        }
        var fluids = new HashSet<IFluidHandler>();
        var all = level.getCapability(Capabilities.FluidHandler.BLOCK, be.getBlockPos(), null);
        if (all != null) fluids.add(all);
        else
            for (Direction side : Direction.values()) {
                var h =
                        level.getCapability(
                                Capabilities.FluidHandler.BLOCK, be.getBlockPos(), side);
                if (h != null) fluids.add(h);
            }
        var seen = new HashSet<String>();
        for (var handler : fluids)
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                var fluid = handler.getFluidInTank(tank);
                if (RadiationSources.profile(fluid) == null) continue;
                String key = tank + ":" + fluid.save(level.registryAccess());
                if (seen.add(key)) result = result.plus(RadiationSources.fluid(fluid, now));
            }
        return result;
    }

    public static void spill(ServerLevel level, BlockEntity be) {
        var source = sample(level, be);
        if (source.activity() <= 0) return;
        double loose = source.activity() * .001;
        RadiationContamination.deposit(
                level,
                be.getBlockPos(),
                new Radionuclides(
                        loose * .8, loose * .199, loose * .001, RadiationSources.now(level)));
    }
}
