package net.createplant.block.entity;

import com.simibubi.create.content.fluids.pump.PumpBlockEntity;

import net.createplant.compat.electro.ElectricUtilityDevice;
import net.createplant.registry.CEElectricUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ElectricPumpBlockEntity extends PumpBlockEntity implements ElectricUtilityDevice.Load {
    public static final double OHMS = 32, CAPACITY = 512;
    private static final String KEY = "electric_utility.create_refinery_n_reactors.";
    private final ElectricUtilityDevice.Input input = new ElectricUtilityDevice.Input();
    private volatile boolean enabled;
    private double watts, joules, work, lastWatts, lastJoules;
    private boolean lastEnabled;

    public ElectricPumpBlockEntity(BlockPos pos, BlockState state) {
        super(CEElectricUtilities.PUMP_ENTITY.get(), pos, state);
    }

    @Override
    public double resistance() {
        return enabled ? OHMS : 0;
    }

    @Override
    public void supply(long time, double voltageSquared) {
        input.supply(time, voltageSquared);
    }

    public double watts() {
        return watts;
    }

    public double storedJoules() {
        return joules;
    }

    public double workJoules() {
        return work;
    }

    public void spendWork(double requested) {
        double used = Math.min(joules, Math.max(0, requested));
        joules -= used;
        work += used;
        setChanged();
    }

    public String status() {
        return !enabled ? "disabled" : watts < 250 ? "power_low" : "running";
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            enabled = !level.hasNeighborSignal(worldPosition);
            watts = enabled ? input.watts(level.getGameTime(), OHMS) : 0;
            double previousJoules = joules;
            joules = Math.min(CAPACITY, joules + watts / 20);
            float next = watts >= 250 ? (float) Math.min(128, 4 * Math.floor(watts / 160)) : 0;
            float old = getSpeed();
            if (old != next) {
                setSpeed(next);
                onSpeedChanged(old);
                sendData();
            }
            if (joules != previousJoules) setChanged();
            if (level.getGameTime() % 10 == 0
                    && (Math.abs(watts - lastWatts) > 1
                            || Math.abs(joules - lastJoules) > 1
                            || enabled != lastEnabled)) {
                lastWatts = watts;
                lastJoules = joules;
                lastEnabled = enabled;
                sendData();
            }
        }
        super.tick();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.write(tag, registries, packet);
        tag.putDouble("MotorJoules", joules);
        tag.putDouble("PumpWork", work);
        if (packet) {
            tag.putDouble("Watts", watts);
            tag.putBoolean("Enabled", enabled);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean packet) {
        super.read(tag, registries, packet);
        joules =
                Double.isFinite(tag.getDouble("MotorJoules"))
                        ? Math.clamp(tag.getDouble("MotorJoules"), 0, CAPACITY)
                        : 0;
        work = Math.max(0, tag.getDouble("PumpWork"));
        watts = packet ? tag.getDouble("Watts") : 0;
        enabled = packet && tag.getBoolean("Enabled");
        if (!packet) setSpeed(0);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> text, boolean sneaking) {
        text.add(Component.translatable(KEY + "state", Component.translatable(KEY + status())));
        text.add(
                Component.translatable(
                        KEY + "power", String.format(java.util.Locale.ROOT, "%.2f", watts / 1000)));
        text.add(Component.translatable(KEY + "rpm", Math.round(getSpeed())));
        text.add(Component.translatable(KEY + "motor", Math.round(joules)));
        return true;
    }
}
