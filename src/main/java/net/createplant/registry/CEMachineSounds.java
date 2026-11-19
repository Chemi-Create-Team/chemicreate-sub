package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEMachineSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CreatePlant.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_CIRCULATION = sound("reactor_circulation");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_DRIVE = sound("fusion_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_HIGH = sound("turbine_high");
    public static final DeferredHolder<SoundEvent, SoundEvent> TURBINE_LOW = sound("turbine_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMPRESSOR = sound("compressor");
    public static final DeferredHolder<SoundEvent, SoundEvent> AGITATOR = sound("agitator");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOT_PROCESS = sound("hot_process");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLUID_PROCESS = sound("fluid_process");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOWER = sound("blower");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRANE_DRIVE = sound("crane_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_ROAR = SOUNDS.register("machine.flare_roar",()->SoundEvent.createFixedRangeEvent(CreatePlant.location("machine.flare_roar"),96));

    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE = sound("centrifuge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUBIC_PRESS = sound("cubic_press");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOILER = sound("boiler");
    public static final DeferredHolder<SoundEvent, SoundEvent> COLDBOX = sound("coldbox");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL_EXCHANGER = sound("shell_exchanger");
    public static final DeferredHolder<SoundEvent, SoundEvent> LARGE_EXCHANGER = sound("large_exchanger");
    public static final DeferredHolder<SoundEvent, SoundEvent> JACKET = sound("jacket");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABSORBER = sound("absorber");
    public static final DeferredHolder<SoundEvent, SoundEvent> PACKED_TOWER = sound("packed_tower");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTION_TOWER = sound("reaction_tower");
    public static final DeferredHolder<SoundEvent, SoundEvent> VERTICAL_SEPARATOR = sound("vertical_separator");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEPARATOR_DRUM = sound("separator_drum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISTILLATION = sound("distillation");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_FURNACE = sound("electric_furnace");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTROLYSIS = sound("electrolysis");
    public static final DeferredHolder<SoundEvent, SoundEvent> COOLING_TOWER = sound("cooling_tower");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONDENSER = sound("condenser");
    public static final DeferredHolder<SoundEvent, SoundEvent> WASTE_MELTER = sound("waste_melter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOLVENT_EXTRACTOR = sound("solvent_extractor");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONVERTER = sound("converter");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL_CELL = sound("fuel_cell");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_CRANE = sound("fusion_crane");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUARRY = sound("quarry");
    public static final DeferredHolder<SoundEvent, SoundEvent> GASIFIER = sound("gasifier");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_GENERATOR = sound("motor_generator");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYWHEEL = sound("flywheel");
    public static final DeferredHolder<SoundEvent, SoundEvent> VACUUM_PUMP = sound("vacuum_pump");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIC_PUMP = sound("electric_pump");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATERIAL_PUMP = sound("material_pump");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELIEF_SCRUBBER = sound("relief_scrubber");
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_HOLDER = sound("gas_holder");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONSTRUCTOR = sound("constructor");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISPATCH = sound("dispatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESSURISED_GAS = sound("pressurised_gas");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIGH_PRESSURE_GAS = sound("high_pressure_gas");

    private CEMachineSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("machine." + name, () -> SoundEvent.createVariableRangeEvent(CreatePlant.location("machine." + name)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
