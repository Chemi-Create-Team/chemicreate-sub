package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The furnace as a resistance between the anode (node 0) and cathode (node 1) terminals of its busbar, only while it is formed. */
public class FurnaceBusbarDevice extends SimpleElectricalDevice {
    public FurnaceBusbarDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) { super(level, pos, data, type); }
    private ElectrolysisFurnaceBlockEntity furnace() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ElectrolysisFurnaceBlockEntity furnace ? furnace : null;
    }
    @Override public void preTick(BridgeCollector collector) {
        var furnace = furnace();
        if (furnace != null && furnace.formed()) collector.builder(pos).resistor(0, 1, ElectrolysisFurnaceBlockEntity.OHMS);
    }
    @Override public void postTick(SimulationResults results) {
        var furnace = furnace();
        if (furnace != null) furnace.setVoltageSquared(furnace.formed() ? results.getVoltageAtSqr(pos, 0, 1) : 0);
    }
}
