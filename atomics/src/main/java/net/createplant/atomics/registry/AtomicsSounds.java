package net.createplant.atomics.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AtomicsSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CreatePlant.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE = sound("centrifuge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONVERTER = sound("converter");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRANE_DRIVE = sound("crane_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUBIC_PRESS = sound("cubic_press");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_CRANE = sound("fusion_crane");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_DRIVE = sound("fusion_drive");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOLVENT_EXTRACTOR = sound("solvent_extractor");
    public static final DeferredHolder<SoundEvent, SoundEvent> WASTE_MELTER = sound("waste_melter");

    private AtomicsSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("machine." + name, () -> SoundEvent.createVariableRangeEvent(CreatePlant.location("machine." + name)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
