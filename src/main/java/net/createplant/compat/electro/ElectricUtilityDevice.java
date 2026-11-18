package net.createplant.compat.electro;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** The two-terminal load of a sealed pump motor. */
public class ElectricUtilityDevice extends SimpleElectricalDevice {
    public interface Load {
        double resistance();

        void supply(long time, double voltageSquared);

        default void supply(long time, double resistance, double voltageSquared) {
            supply(time, voltageSquared);
        }
    }

    public static final class Input {
        private volatile double squared;
        private volatile double suppliedResistance;
        private volatile long suppliedAt = Long.MIN_VALUE;

        public void supply(long time, double voltageSquared) {
            supply(time, 0, voltageSquared);
        }

        public void supply(long time, double resistance, double voltageSquared) {
            suppliedResistance = Double.isFinite(resistance) ? Math.max(0, resistance) : 0;
            squared = Double.isFinite(voltageSquared) ? Math.max(0, voltageSquared) : 0;
            suppliedAt = time;
        }

        public double watts(long time, double resistance) {
            double solved = suppliedResistance > 0 ? suppliedResistance : resistance;
            return suppliedAt >= time - 1 && solved > 0 ? squared / solved : 0;
        }

        public double volts(long time) {
            return suppliedAt >= time - 1 ? Math.sqrt(squared) : 0;
        }
    }

    private double resistance;

    public ElectricUtilityDevice(
            Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    private Load load() {
        return level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof Load load
                ? load
                : null;
    }

    @Override
    public void preTick(BridgeCollector collector) {
        var load = load();
        resistance = load == null ? 0 : load.resistance();
        if (resistance > 0) collector.builder(pos).resistor(0, 1, resistance);
    }

    @Override
    public void postTick(SimulationResults results) {
        var load = load();
        if (load != null)
            load.supply(
                    level.getGameTime(),
                    resistance,
                    resistance > 0 ? results.getVoltageAtSqr(pos, 0, 1) : 0);
    }
}
