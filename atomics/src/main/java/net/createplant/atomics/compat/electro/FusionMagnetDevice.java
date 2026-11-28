package net.createplant.atomics.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.atomics.block.entity.FusionReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Pulsed-power load for charging the superconducting coils and heating the initial plasma. */
public class FusionMagnetDevice extends SimpleElectricalDevice {
    private double ohms;
    public FusionMagnetDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }
    private FusionReactorControllerBlockEntity controller() {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity controller ? controller : null;
    }
    @Override public void preTick(BridgeCollector collector) {
        var controller = controller();
        double demand = controller == null ? 0 : controller.electricalDemandWatts();
        double volts = net.createplant.atomics.block.entity.FusionMotorGeneratorBlockEntity.INTERNAL_VOLTS;
        ohms = demand > 0 ? volts * volts / demand : 0;
        if (ohms > 0) collector.builder(pos).resistor(0, 1, ohms);
    }
    @Override public void postTick(SimulationResults results) {
        var controller = controller();
        if (controller != null) controller.supplyElectrical(ohms > 0 ? results.getVoltageAtSqr(pos, 0, 1) / ohms : 0,
            ohms > 0 ? Math.sqrt(Math.max(0, results.getVoltageAtSqr(pos, 0, 1))) : 0);
    }
}
