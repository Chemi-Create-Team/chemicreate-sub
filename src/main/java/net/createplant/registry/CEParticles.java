package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class CEParticles {
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, CreatePlant.MOD_ID);
    public static final Supplier<SimpleParticleType> TOWER_STEAM = TYPES.register("tower_steam", () -> new SimpleParticleType(true));
    private CEParticles() { }
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
