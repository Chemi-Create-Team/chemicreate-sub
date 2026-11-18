package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.block.entity.FuelCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The fuel cell as a voltage source behind its internal resistance between the positive (node 0) and negative (node 1) terminals, while it has fuel. */
public class FuelCellDevice extends SimpleElectricalDevice {
    private boolean sourced;
    public FuelCellDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) { super(level, pos, data, type); }
    private FuelCellBlockEntity cell() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FuelCellBlockEntity cell ? cell : null;
    }
    @Override public void preTick(BridgeCollector collector) {
        var cell = cell();
        sourced = cell != null && cell.ready(level.getGameTime());
        // Electro Energetics takes the resistance before the voltage, and raises the second node: node 1 to node 0 makes node 0 the positive terminal.
        if (sourced) collector.builder(pos).voltageSourceWithResistance(1, 0, FuelCellBlockEntity.OHMS, FuelCellBlockEntity.OPEN_VOLTS);
    }
    @Override public void postTick(SimulationResults results) {
        var cell = cell();
        if (cell != null) cell.supplied(sourced ? results.getVoltageAt(pos, 0, 1) : 0, sourced, level.getGameTime());
    }
}
