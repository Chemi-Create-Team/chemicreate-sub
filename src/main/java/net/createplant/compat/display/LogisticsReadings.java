package net.createplant.compat.display;

import net.createplant.block.entity.*;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.createplant.compat.display.MachineReadings.Reading;

/** The logistics machines' display-link readings and status, hooked into {@link MachineReadings}. */
public final class LogisticsReadings implements MachineReadings.Addon {
    private LogisticsReadings() { }

    public static void register() {
        MachineReadings.register(new LogisticsReadings());
    }

    @Override public Reading read(BlockEntity original, String key, Direction face) {
        if (original instanceof IndustrialDispatchBlockEntity dispatch) {
            if (key.equals("dispatch_completed")) return new Reading(dispatch.completed(), 0);
            if (key.equals("progress")) { var order = dispatch.order();return order == null ? null : new Reading(dispatch.progress(), order.value().getProcessingDuration()); }
            if (key.equals("fluid_0")) return new Reading(dispatch.cargo().getFluidAmount(), dispatch.cargo().getCapacity());
            if (key.equals("item_0")) return new Reading(dispatch.rewards().getStackInSlot(0).getCount(), 64);
        }
        return MachineReadings.PASS;
    }

    @Override public Component status(BlockEntity original) {
        if (original instanceof IndustrialDispatchBlockEntity dispatch) return Component.translatable("industrial_dispatch." + dispatch.status());
        return null;
    }

    @Override public net.minecraft.network.chat.MutableComponent line(String metric, BlockEntity source) {
        if (metric.equals("dispatch_order") && source instanceof IndustrialDispatchBlockEntity dispatch) return dispatch.orderDescription().copy();
        return null;
    }
}
