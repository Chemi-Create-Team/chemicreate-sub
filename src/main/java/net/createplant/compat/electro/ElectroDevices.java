package net.createplant.compat.electro;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import net.createplant.CreatePlant;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Devices of this mod in the Electro Energetics circuit. */
public final class ElectroDevices {
    private static final DeferredRegister<SimulatedDeviceType<?>> DEVICES = DeferredRegister.create(CEERegistries.SIMULATED_DEVICE_TYPE, CreatePlant.MOD_ID);
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FurnaceBusbarDevice>> FURNACE = DEVICES.register("electrolysis_furnace",
        () -> new SimulatedDeviceType<>(CreatePlant.location("electrolysis_furnace"), (type, level, pos, data) -> new FurnaceBusbarDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<VitrificationMelterDevice>> VITRIFICATION = DEVICES.register("vitrification_melter",
        () -> new SimulatedDeviceType<>(CreatePlant.location("vitrification_melter"), (type, level, pos, data) -> new VitrificationMelterDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ElectrolysisCellDevice>> ELECTROLYZER = DEVICES.register("electrolysis_electrode_head",
        () -> new SimulatedDeviceType<>(CreatePlant.location("electrolysis_electrode_head"), (type, level, pos, data) -> new ElectrolysisCellDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FuelCellDevice>> FUEL_CELL = DEVICES.register("fuel_cell",
        () -> new SimulatedDeviceType<>(CreatePlant.location("fuel_cell"), (type, level, pos, data) -> new FuelCellDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<FusionMagnetDevice>> FUSION_MAGNET = DEVICES.register("fusion_magnet_feeder",
        () -> new SimulatedDeviceType<>(CreatePlant.location("fusion_magnet_feeder"), (type, level, pos, data) -> new FusionMagnetDevice(level, pos, data, type)));
    public static final DeferredHolder<SimulatedDeviceType<?>, SimulatedDeviceType<ElectricUtilityDevice>> PUMP = DEVICES.register("electric_pump",
        () -> new SimulatedDeviceType<>(CreatePlant.location("electric_pump"), (type, level, pos, data) -> new ElectricUtilityDevice(level, pos, data, type)));
    private ElectroDevices() { }
    public static void register(IEventBus bus) { DEVICES.register(bus); }
}
