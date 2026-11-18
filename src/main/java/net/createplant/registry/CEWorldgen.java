package net.createplant.registry;

import net.createplant.CreatePlant;
import net.createplant.worldgen.DeepLayerPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEWorldgen {
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, CreatePlant.MOD_ID);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<DeepLayerPlacement>> DEEP_LAYER =
        PLACEMENT_MODIFIERS.register("deep_layer", () -> () -> DeepLayerPlacement.CODEC);

    private CEWorldgen() { }

    public static void register(IEventBus bus) { PLACEMENT_MODIFIERS.register(bus); }
}
