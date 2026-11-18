package net.createplant.registry;

import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Resolves ids saved under the previous mod id to the current entries, so blocks, items, block entities and fluids in worlds
 * from before the rename to Create: Refinery 'n' Reactors load instead of being dropped.
 */
public final class CELegacyIds {
    private static final String PREVIOUS_MOD_ID = "create_heavy_indusstry";
    /** Retired entries merged into another one: saved blocks, items and block entities of the old id load as the new one. */
    private static final Map<String, String> MERGED = Map.of("tubular_boiler", "horizontal_fluid_tank",
        "gasifier_casing", "steam_gasifier", "water_cooled_refractory_wall", "steam_gasifier", "entrained_burner_nozzle", "steam_gasifier",
        "slag_tap_hearth", "steam_gasifier", "quench_sight_glass", "steam_gasifier");

    private CELegacyIds() { }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOWEST, CELegacyIds::addAliases);
    }

    private static void addAliases(RegisterEvent event) {
        Registry<?> registry = event.getRegistry();
        for (ResourceLocation id : registry.keySet()) {
            if (id.getNamespace().equals(CreatePlant.MOD_ID))
                registry.addAlias(ResourceLocation.fromNamespaceAndPath(PREVIOUS_MOD_ID, id.getPath()), id);
        }
        for (var merged : MERGED.entrySet()) {
            var target = ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, merged.getValue());
            if (!registry.containsKey(target)) continue;
            for (String namespace : List.of(CreatePlant.MOD_ID, PREVIOUS_MOD_ID)) {
                var retired = ResourceLocation.fromNamespaceAndPath(namespace, merged.getKey());
                if (!registry.containsKey(retired)) registry.addAlias(retired, target);
            }
        }
    }
}
