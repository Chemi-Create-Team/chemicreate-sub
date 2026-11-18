package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.block.entity.FuelCycleMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class VitrificationMelterDevice extends SimpleElectricalDevice {
    public VitrificationMelterDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) { super(level, pos, data, type); }
    private FuelCycleMachineBlockEntity melter() { return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FuelCycleMachineBlockEntity be ? be : null; }
    @Override public void preTick(BridgeCollector collector) { var be = melter();if (be != null && be.formed()) collector.builder(pos).resistor(0, 1, FuelCycleMachineBlockEntity.OHMS); }
    @Override public void postTick(SimulationResults results) { var be = melter();if (be != null) be.setVoltageSquared(results.getVoltageAtSqr(pos, 0, 1)); }
}
