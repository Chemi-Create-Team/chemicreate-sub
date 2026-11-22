package net.createplant.registry;

import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
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
    private static final ResourceLocation NOZZLE = ResourceLocation.fromNamespaceAndPath("create", "nozzle");
    /** Retired entries merged into another one: saved blocks, items and block entities of the old id load as the new one. */
    private static final Map<String, String> MERGED = Map.ofEntries(Map.entry("tubular_boiler", "horizontal_fluid_tank"),
        Map.entry("gasifier_casing", "steam_gasifier"), Map.entry("water_cooled_refractory_wall", "steam_gasifier"), Map.entry("entrained_burner_nozzle", "steam_gasifier"),
        Map.entry("slag_tap_hearth", "steam_gasifier"), Map.entry("quench_sight_glass", "steam_gasifier"),
        // Retired pipe materials load as the remaining pipe that covers their duty: metal lines as stainless steel, plastic ones as stoneware.
        Map.entry("gas_pipe", "stainless_steel_pipe"), Map.entry("large_gas_pipe", "stainless_steel_pipe"), Map.entry("reinforced_pipe", "stainless_steel_pipe"),
        Map.entry("cryogenic_pipe", "stainless_steel_pipe"), Map.entry("resin_pipe", "ceramic_pipe"), Map.entry("pvc_pipe", "ceramic_pipe"),
        Map.entry("reinforced_pump", "stainless_steel_pump"), Map.entry("cryogenic_pump", "stainless_steel_pump"), Map.entry("gas_pump", "stainless_steel_pump"),
        Map.entry("large_gas_pump", "stainless_steel_pump"), Map.entry("resin_pump", "stainless_steel_pump"), Map.entry("pvc_pump", "stainless_steel_pump"),
        // The reaction tower's catalyst basket became the reaction tray, which holds the catalyst as well as solid feed.
        Map.entry("reactor_catalyst_basket", "reactor_separator_core"));

    private CELegacyIds() { }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOWEST, CELegacyIds::addAliases);
    }

    public static boolean wasAirFilter(String id) {
        return id.equals(CreatePlant.MOD_ID + ":air_filter") || id.equals(PREVIOUS_MOD_ID + ":air_filter");
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
        var key = registry.key();
        // The retired resin pipe had its own block entity type; its stoneware replacement uses the material pipe one.
        var materialPipe = CreatePlant.location("material_pipe");
        if (key.equals(Registries.BLOCK_ENTITY_TYPE) && registry.containsKey(materialPipe))
            for (String namespace : List.of(CreatePlant.MOD_ID, PREVIOUS_MOD_ID)) registry.addAlias(ResourceLocation.fromNamespaceAndPath(namespace, "resin_pipe"), materialPipe);
        if ((key.equals(Registries.BLOCK) || key.equals(Registries.ITEM) || key.equals(Registries.BLOCK_ENTITY_TYPE)) && registry.containsKey(NOZZLE)) {
            for (String namespace : List.of(CreatePlant.MOD_ID, PREVIOUS_MOD_ID))
                registry.addAlias(ResourceLocation.fromNamespaceAndPath(namespace, "air_filter"), NOZZLE);
        }
    }
}
