package net.createplant.compat.electro;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.createplant.CreatePlant;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Atomics devices in the Electro Energetics circuit (ids stay in the core namespace). */
public final class AtomicsElectroDevices {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, CreatePlant.MOD_ID);
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<VitrificationMelterDevice>> VITRIFICATION = DEVICES.register("vitrification_melter",
        () -> new SimulatedDeviceType<>(CreatePlant.location("vitrification_melter"), (type, level, pos, data) -> new VitrificationMelterDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FusionMagnetDevice>> FUSION_MAGNET = DEVICES.register("fusion_magnet_feeder",
        () -> new SimulatedDeviceType<>(CreatePlant.location("fusion_magnet_feeder"), (type, level, pos, data) -> new FusionMagnetDevice(level, pos, data, type)));
    private AtomicsElectroDevices() { }
    public static void register(IEventBus bus) { DEVICES.register(bus); }
}
