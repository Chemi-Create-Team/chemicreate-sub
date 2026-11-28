package net.createplant.logistics.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LogisticsSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CreatePlant.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> DISPATCH = sound("dispatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> QUARRY = sound("quarry");

    private LogisticsSounds() { }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("machine." + name, () -> SoundEvent.createVariableRangeEvent(CreatePlant.location("machine." + name)));
    }

    public static void register(IEventBus bus) { SOUNDS.register(bus); }
}
