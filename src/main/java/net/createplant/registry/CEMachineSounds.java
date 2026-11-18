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

    private CEMachineSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("machine." + name, () -> SoundEvent.createVariableRangeEvent(CreatePlant.location("machine." + name)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
