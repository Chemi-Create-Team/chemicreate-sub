package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Only the two terminals on the upper panel unit draw the stack's measured circuit power. */
public class ElectrolysisCellDevice extends SimpleElectricalDevice {
    private double resistance;
    public ElectrolysisCellDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) { super(level, pos, data, type); }
    private ElectrolysisControllerBlockEntity owner() {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(net.createplant.registry.CEElectrolysis.CONTROLLER.get())
            && level.getBlockState(pos).getValue(ElectrolysisControllerBlock.PART) == 1 && level.getBlockEntity(pos) instanceof ElectrolysisControllerBlockEntity part ? part.master() : null;
    }
    @Override public void preTick(BridgeCollector collector) {
        var owner = owner();
        resistance = owner == null ? 0 : owner.resistance();
        if (resistance > 0) collector.builder(pos).resistor(0, 1, resistance);
    }
    @Override public void postTick(SimulationResults results) {
        var owner = owner();
        if (owner != null) owner.supply(level.getGameTime(), resistance, resistance > 0 ? results.getVoltageAtSqr(pos, 0, 1) : 0);
    }
}
