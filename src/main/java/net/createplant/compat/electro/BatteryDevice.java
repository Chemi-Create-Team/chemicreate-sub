package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.block.entity.BatteryTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A battery bank as a voltage source behind its resistance between the positive (node 0) and negative (node 1) terminals; current can flow either way. */
public class BatteryDevice extends SimpleElectricalDevice {
    private boolean sourced;
    public BatteryDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) { super(level, pos, data, type); }
    private BatteryTerminalBlockEntity bank() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BatteryTerminalBlockEntity bank ? bank : null;
    }
    @Override public void preTick(BridgeCollector collector) {
        var bank = bank();
        sourced = bank != null && bank.ready(level.getGameTime());
        // Same node order as the fuel cell: resistance first, then voltage, node 1 to node 0 makes node 0 the positive terminal.
        if (sourced) collector.builder(pos).voltageSourceWithResistance(1, 0, bank.ohms(), bank.emf());
    }
    @Override public void postTick(SimulationResults results) {
        var bank = bank();
        if (bank != null) bank.supplied(sourced ? results.getVoltageAt(pos, 0, 1) : 0, sourced, level.getGameTime());
    }
}
