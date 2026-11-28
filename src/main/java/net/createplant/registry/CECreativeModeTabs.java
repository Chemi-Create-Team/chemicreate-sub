package net.createplant.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.item.GasBucketItem;
import net.createplant.item.StatefulBuckets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CECreativeModeTabs {
    /** Equipment items addons add after the core equipment (e.g. radiation meters). */
    public static final java.util.List<java.util.function.Supplier<? extends Item>> EXTRA_EQUIPMENT = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static boolean isExtra(Item item) {
        for (var extra : EXTRA_EQUIPMENT) if (extra.get() == item) return true;
        return false;
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreatePlant.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE = TABS.register(
        "base",
        () -> CreativeModeTab.builder()
            .withTabFactory(SectionedCreativeTab::new)
            .hideTitle()
            .title(Component.translatable("itemGroup.create_refinery_n_reactors.base"))
            .icon(() -> CETransport.LIDDED_VESSEL.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> display(only(output, CreatePlant.MOD_ID)))
            .build()
    );

    /** Which mod registered each item; all three mods share one namespace, so the id cannot tell. Unknown items count as core. */
    private static final Map<Item, String> OWNERS = new ConcurrentHashMap<>();

    static {
        // RegisterEvent runs each mod's listeners inside that mod's container, so the active namespace is the registering mod.
        BuiltInRegistries.ITEM.addCallback((net.neoforged.neoforge.registries.callback.AddCallback<Item>) (registry, id, key, item) -> {
            if (key.location().getNamespace().equals(CreatePlant.MOD_ID))
                OWNERS.put(item, net.neoforged.fml.ModLoadingContext.get().getActiveNamespace());
        });
    }

    /** Output that passes on only the items of one mod; vanilla and other mods' items belong to core. */
    private static CreativeModeTab.Output only(CreativeModeTab.Output output, String owner) {
        return (stack, visibility) -> {
            if (OWNERS.getOrDefault(stack.getItem(), CreatePlant.MOD_ID).equals(owner)) output.accept(stack, visibility);
        };
    }

    public static DeferredHolder<CreativeModeTab, CreativeModeTab> addonTab(
        DeferredRegister<CreativeModeTab> tabs, String owner, String titleKey, Supplier<ItemStack> icon) {
        return tabs.register("base", () -> CreativeModeTab.builder()
            .withTabFactory(SectionedCreativeTab::new)
            .hideTitle()
            .withTabsBefore(BASE.getId())
            .title(Component.translatable(titleKey))
            .icon(icon)
            .displayItems((parameters, output) -> display(only(output, owner)))
            .build());
    }

    /** Material groups follow the equipment, after the liquid and gas buckets. */
    private static final List<List<String>> MATERIAL_GROUPS = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
        List.of("bauxite_ore", "deepslate_bauxite_ore", "pentlandite_ore", "deepslate_pentlandite_ore", "ilmenite_ore", "deepslate_ilmenite_ore",
            "zircon_ore", "deepslate_zircon_ore", "chromite_ore", "deepslate_chromite_ore", "molybdenite_ore", "deepslate_molybdenite_ore",
            "spodumene_ore", "deepslate_spodumene_ore", "galena_ore", "deepslate_galena_ore", "halite_ore", "deepslate_halite_ore"),
        List.of("bauxite", "pentlandite", "ilmenite", "zircon", "chromite", "molybdenite", "spodumene", "galena", "fluorite", "borax", "gypsum",
            "asbestos"),
        List.of("salt", "sulfur_dust", "alumina", "titanium_dioxide", "zirconia", "zirconium_sulfate_hydrate", "zirconyl_sulfate",
            "titanyl_sulfate_hydrate", "aluminium_sulfate_hydrate", "iron_sulfate_hydrate", "copper_sulfate_crystals", "zinc_sulfate_hydrate",
            "tin_sulfate_crystals", "lead_chloride_crystals", "uranyl_nitrate_hydrate", "plutonium_nitrate_crystals", "sodium_aluminate_crystals",
            "caustic_soda_flakes", "ammonium_chloride_crystals", "nickel_oxide", "molybdenum_trioxide", "chromium_oxide", "iron_oxide_red",
            "nickel_titanium_yellow", "molybdenum_blue", "triiron_tetroxide_pile", "ammonium_nitrate", "ammonium_sulfate", "quicklime",
            "sodium_bicarbonate", "sodium_carbonate", "lithium_carbonate", "lead_sinter", "urea", "potash", "phosphate_fertilizer", "npk_fertilizer",
            "pvc_resin", "cement_clinker", "ground_clinker", "cement_powder", "water_treatment_flocculant", "treatment_sludge", "charred_wood"),
        List.of("carbon_powder", "unprocessed_steel", "pig_iron", "heated_unfinished_steel", "steel_slag", "steel_ingot", "steel_block",
            "ferrochrome", "aluminum_ingot", "lead_ingot", "nickel_ingot", "titanium_ingot", "zirconium_ingot", "chromium_ingot", "molybdenum_ingot",
            "stainless_steel_ingot", "aluminum_sheet", "nickel_sheet", "titanium_sheet", "stainless_steel_sheet", "iron_pipe",
            "unfired_ceramic_pipe", "zircaloy_cladding_tube"),
        List.of("polyethylene", "polypropylene", "synthetic_rubber", "industrial_resin", "plastic_sheet", "rubber_gasket", "flame_retardant_pellet",
            "heat_resistant_gasket", "flexible_rubber_hose", "paraffin_wax", "phenolic_resin", "phenolic_board", "phosphate_compound",
            "heat_resistant_coating", "fire_resistant_conductor", "fire_resistant_cable"),
        List.of("iron_catalyst", "oxidation_catalyst", "cracking_catalyst", "polymerization_catalyst", "hydrotreating_catalyst",
            "hydrocracking_catalyst", "shift_catalyst_core", "iron_ft_catalyst_core", "cobalt_ft_catalyst_core", "zsm5_catalyst_core",
            "cumene_catalyst_core", "platinum_zeolite_core", "water_filter_medium"),
        List.of("nickel_electrode_plate", "asbestos_wrapped_electrode", "high_pressure_turbine_blade", "low_pressure_turbine_blade", "tarbin_end",
            "low_pressure_turbine_end"),
        List.of("fire_extinguisher", "flamethrower", "chemical_sprayer", "steam_gun", "steam_hammer", "pipe_configurator", "gas_mask",
            "gas_mask_filter", "inert_gas_cylinder", "protective_cap", "protective_coat", "protective_trousers", "protective_shoe_covers",
            "hazard_helmet", "hazard_chestplate", "hazard_leggings", "hazard_boots", "soap")
    ));

    /** Equipment and construction groups in display order. */
    private static final List<List<String>> EQUIPMENT_GROUPS = new java.util.concurrent.CopyOnWriteArrayList<>(List.of(
        List.of("industrial_concrete", "industrial_concrete_slab", "industrial_concrete_stairs", "corrosion_resistant_floor",
            "corrosion_resistant_floor_slab", "molded_polymer", "industrial_glass", "stainless_steel_block", "titanium_block", "diorite_casing",
            "tank_casing"),
        List.of("nickel_alloy_pipe", "zirconium_pipe", "stainless_steel_pipe", "ceramic_pipe"),
        List.of("nickel_alloy_pump", "zirconium_pump", "stainless_steel_pump"),
        List.of("pressure_reducing_valve", "relief_scrubber", "gas_detector", "gas_recovery_duct", "flow_regulator", "pipe_thermometer",
            "pipe_pressure_gauge", "pipe_flow_meter", "process_transmitter", "control_valve_actuator", "pid_controller", "pid_instrument_rack"),
        List.of("horizontal_fluid_tank", "stainless_boiler", "pressure_tank", "stainless_steel_tank", "cryogenic_tank", "gas_receiver",
            "gas_tank_container", "high_pressure_separator", "gas_holder", "lidded_vessel", "stirred_tank", "ammonia_separator"),
        List.of("high_pressure_turbine_unit", "low_pressure_turbine_unit", "large_flywheel", "high_pressure_turbine_casing",
            "low_pressure_turbine_casing", "reciprocating_cylinder", "reciprocating_compressor"),
        List.of("electric_pump", "high_pressure_pump", "hose_connector", "agricultural_sprayer", "tube_piped_tank", "jacketed_pipe",
            "cooler_radiator", "cooling_fan", "cooling_fan_shroud", "natural_draft_cooling_tower", "cooling_tower_shell", "cooling_tower_basin",
            "cooling_tower_fill", "cooling_tower_distributor", "cooling_tower_hot_inlet", "cooling_tower_cold_outlet", "cooling_tower_makeup_inlet",
            "condenser_waterbox", "condenser_shell", "condenser_tube_bundle", "condenser_cooling_inlet", "condenser_cooling_outlet",
            "cryogenic_cold_box_drive", "cryogenic_regenerator"),
        List.of("fractionating_column", "vessel_stirrer", "reactor_impeller", "reactor_separator_core", "large_absorption_tower", "reaction_tower",
            "electrolysis_cell", "electrolysis_electrode_head", "electrolysis_cell_controller", "electrolysis_furnace_shell",
            "electrolysis_furnace_anode", "electrolysis_furnace_busbar", "fuel_cell", "lead_acid_battery_casing", "lead_acid_battery_terminal",
            "lithium_ion_battery_casing", "lithium_ion_battery_terminal", "flare_tip"),
        List.of("facility_constructor", "vault_hatch")
    ));

    private static void display(CreativeModeTab.Output output) {
        display(output, false);
        display(output, true);
        EQUIPMENT_GROUPS.getFirst().stream().map(CECreativeModeTabs::item).filter(i -> i != Items.AIR).forEach(output::accept);
        EXTRA_EQUIPMENT.forEach(item -> output.accept(item.get()));
    }

    private static void display(CreativeModeTab.Output output, boolean materials) {
        var groups = (materials ? MATERIAL_GROUPS : EQUIPMENT_GROUPS.subList(1, EQUIPMENT_GROUPS.size())).stream()
            .map(paths -> paths.stream().map(CECreativeModeTabs::item).filter(i -> i != Items.AIR).toList()).toList();
        var listed = new HashSet<Item>();
        for (var paths : MATERIAL_GROUPS) for (var path : paths) listed.add(item(path));
        for (var paths : EQUIPMENT_GROUPS) for (var path : paths) listed.add(item(path));
        listed.add(CEWorldFluids.EMPTY_ALLOY_BUCKET.get());
        listed.add(CEWorldFluids.STAINLESS_BUCKET.get());
        listed.add(CESafety.RAW_PROCESS_GAS.get().getBucket());
        // Dyed flywheels are made by dyeing the placed yellow one.
        CEItems.LARGE_FLYWHEELS.forEach(item -> listed.add(item.get()));
        var liquids = new ArrayList<Item>();
        var gases = new ArrayList<Item>();
        var rest = new ArrayList<Item>();
        BuiltInRegistries.ITEM.stream()
            .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CreatePlant.MOD_ID) && !listed.contains(item))
            .forEach(item -> {
                if (isExtra(item)) return;
                if (item instanceof GasBucketItem) gases.add(item);
                else if (item instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) liquids.add(item);
                else if (!(item instanceof BlockItem) == materials) rest.add(item);
            });
        if (materials) {
            output.accept(CEWorldFluids.EMPTY_ALLOY_BUCKET.get());
            output.accept(CEWorldFluids.STAINLESS_BUCKET.get());
            output.accept(Items.WATER_BUCKET);
            liquids.forEach(output::accept);
            output.accept(com.jesz.createdieselgenerators.CDGFluids.CRUDE_OIL.getBucket().get());
            gases.forEach(output::accept);
        }
        for (var group : groups) group.forEach(output::accept);
        rest.forEach(output::accept);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.get(CreatePlant.location(path));
    }

    static String section(Item item) {
        if (isExtra(item)) return "equipment";
        if (item == CEWorldFluids.EMPTY_ALLOY_BUCKET.get() || item == CEWorldFluids.STAINLESS_BUCKET.get() || item instanceof GasBucketItem
            || item instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) return "materials";
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (EQUIPMENT_GROUPS.getFirst().contains(path) || path.equals("steel_block")) return "construction";
        for (int i = 1; i < EQUIPMENT_GROUPS.size(); i++) if (EQUIPMENT_GROUPS.get(i).contains(path)) return "equipment";
        if (MATERIAL_GROUPS.getLast().contains(path)) return "equipment";
        for (var group : MATERIAL_GROUPS) if (group.contains(path)) return "materials";
        return item instanceof BlockItem ? "equipment" : "materials";
    }

    private static final java.util.Map<String, Integer> ADDED_AFTER = new java.util.HashMap<>();

    private static List<List<String>> groupWith(String anchor) {
        return MATERIAL_GROUPS.stream().anyMatch(group -> group.contains(anchor)) ? MATERIAL_GROUPS : EQUIPMENT_GROUPS;
    }

    private static synchronized void insert(String anchor, int offset, String[] paths) {
        var groups = groupWith(anchor);
        for (int i = 0; i < groups.size(); i++) {
            var group = new ArrayList<>(groups.get(i));
            int at = group.indexOf(anchor);
            if (at < 0) continue;
            group.addAll(at + offset, List.of(paths));
            groups.set(i, List.copyOf(group));
            return;
        }
    }

    /** Places an addon's items right after a core item of the tab. */
    public static void insertAfter(String anchor, String... paths) { insert(anchor, 1, paths); }

    /** Places an addon's items right before a core item of the tab. */
    public static void insertBefore(String anchor, String... paths) { insert(anchor, 0, paths); }

    /** Starts a new group of an addon's items after the group of a core item; groups added after one item keep their order. */
    public static synchronized void addGroupAfter(String anchor, String... paths) {
        var groups = groupWith(anchor);
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).contains(anchor)) {
            groups.add(i + 1 + ADDED_AFTER.merge(anchor, 1, Integer::sum) - 1, List.of(paths));
            return;
        }
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
