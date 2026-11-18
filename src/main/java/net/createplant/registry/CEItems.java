package net.createplant.registry;

import java.util.List;

import net.createplant.CreatePlant;
import net.createplant.item.GasBucketItem;
import net.createplant.item.TurbineBladeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CEItems {
    /** Batches a catalyst runs in the catalytic pressure vessel before it is spent; metal brings a spent one back. */
    public static final int CATALYST_BATCHES = 4000;
    /** Not repairable by joining two worn ones: only the regeneration recipes bring a catalyst back. */
    private static Item.Properties catalyst() { return new Item.Properties().durability(CATALYST_BATCHES).setNoRepair(); }
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);

    public static final DeferredItem<BlockItem> DIORITE_CASING = ITEMS.registerSimpleBlockItem(CEBlocks.DIORITE_CASING);
    public static final DeferredItem<BlockItem> RESIN_PIPE = ITEMS.registerSimpleBlockItem(CEBlocks.RESIN_PIPE);
    public static final DeferredItem<BlockItem> TRIIRON_TETROXIDE_PILE = ITEMS.registerSimpleBlockItem(CEBlocks.TRIIRON_TETROXIDE_PILE);
    public static final DeferredItem<BlockItem> INDUSTRIAL_GLASS = ITEMS.registerSimpleBlockItem(CEBlocks.INDUSTRIAL_GLASS);
    public static final DeferredItem<Item> ASBESTOS = ITEMS.registerSimpleItem("asbestos");
    public static final DeferredItem<Item> CRACKING_CATALYST = ITEMS.registerSimpleItem("cracking_catalyst", catalyst());
    public static final DeferredItem<Item> POLYMERIZATION_CATALYST = ITEMS.registerSimpleItem("polymerization_catalyst", catalyst());
    public static final DeferredItem<Item> POLYETHYLENE = ITEMS.registerSimpleItem("polyethylene");
    public static final DeferredItem<Item> POLYPROPYLENE = ITEMS.registerSimpleItem("polypropylene");
    public static final DeferredItem<Item> SYNTHETIC_RUBBER = ITEMS.registerSimpleItem("synthetic_rubber");
    public static final DeferredItem<Item> PLASTIC_SHEET = ITEMS.registerSimpleItem("plastic_sheet");
    public static final DeferredItem<Item> RUBBER_GASKET = ITEMS.registerSimpleItem("rubber_gasket");
    public static final DeferredItem<Item> INDUSTRIAL_RESIN = ITEMS.registerSimpleItem("industrial_resin");
    public static final DeferredItem<net.createplant.item.ProcessBucketItem> HEAVY_OIL_BUCKET = ITEMS.register("heavy_oil_bucket",
        () -> new net.createplant.item.ProcessBucketItem(CEPetroFluids.HEAVY_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<net.createplant.item.ProcessBucketItem> NAPHTHA_BUCKET = ITEMS.register("naphtha_bucket",
        () -> new net.createplant.item.ProcessBucketItem(CEPetroFluids.NAPHTHA.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<net.createplant.item.ProcessBucketItem> LUBRICATING_OIL_BUCKET = ITEMS.register("lubricating_oil_bucket",
        () -> new net.createplant.item.ProcessBucketItem(CEPetroFluids.LUBRICATING_OIL.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<GasBucketItem> ETHYLENE_BUCKET = ITEMS.register("ethylene_bucket",
        () -> new GasBucketItem(CEPetroFluids.ETHYLENE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<GasBucketItem> PROPYLENE_BUCKET = ITEMS.register("propylene_bucket",
        () -> new GasBucketItem(CEPetroFluids.PROPYLENE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<Item> OXIDATION_CATALYST = ITEMS.registerSimpleItem("oxidation_catalyst", catalyst());
    public static final DeferredItem<Item> HYDROTREATING_CATALYST = ITEMS.registerSimpleItem("hydrotreating_catalyst", catalyst());
    public static final DeferredItem<Item> HYDROCRACKING_CATALYST = ITEMS.registerSimpleItem("hydrocracking_catalyst", catalyst());
    public static final DeferredItem<GasBucketItem> NITRIC_OXIDE_BUCKET = ITEMS.register("nitric_oxide_bucket",
        () -> new GasBucketItem(CEOstwaldFluids.NITRIC_OXIDE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<GasBucketItem> NITROGEN_DIOXIDE_BUCKET = ITEMS.register("nitrogen_dioxide_bucket",
        () -> new GasBucketItem(CEOstwaldFluids.NITROGEN_DIOXIDE.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<BlockItem> TANK_CASING = ITEMS.registerSimpleBlockItem(CEBlocks.TANK_CASING);
    public static final DeferredItem<BlockItem> LARGE_FLYWHEEL = ITEMS.registerSimpleBlockItem(CEBlocks.LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> WHITE_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.WHITE_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> ORANGE_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.ORANGE_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> MAGENTA_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.MAGENTA_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> LIGHT_BLUE_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.LIGHT_BLUE_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> LIME_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.LIME_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> PINK_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.PINK_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> GRAY_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.GRAY_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> LIGHT_GRAY_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.LIGHT_GRAY_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> CYAN_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.CYAN_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> PURPLE_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.PURPLE_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> BLUE_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.BLUE_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> BROWN_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.BROWN_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> GREEN_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.GREEN_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> RED_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.RED_LARGE_FLYWHEEL);
    public static final DeferredItem<BlockItem> BLACK_LARGE_FLYWHEEL =
        ITEMS.registerSimpleBlockItem(CEBlocks.BLACK_LARGE_FLYWHEEL);
    public static final List<DeferredItem<BlockItem>> LARGE_FLYWHEELS = List.of(
        LARGE_FLYWHEEL,
        WHITE_LARGE_FLYWHEEL,
        ORANGE_LARGE_FLYWHEEL,
        MAGENTA_LARGE_FLYWHEEL,
        LIGHT_BLUE_LARGE_FLYWHEEL,
        LIME_LARGE_FLYWHEEL,
        PINK_LARGE_FLYWHEEL,
        GRAY_LARGE_FLYWHEEL,
        LIGHT_GRAY_LARGE_FLYWHEEL,
        CYAN_LARGE_FLYWHEEL,
        PURPLE_LARGE_FLYWHEEL,
        BLUE_LARGE_FLYWHEEL,
        BROWN_LARGE_FLYWHEEL,
        GREEN_LARGE_FLYWHEEL,
        RED_LARGE_FLYWHEEL,
        BLACK_LARGE_FLYWHEEL
    );
    public static final DeferredItem<TurbineBladeItem> HIGH_PRESSURE_TURBINE_BLADE = ITEMS.register(
        "high_pressure_turbine_blade",
        () -> new TurbineBladeItem(new Item.Properties())
    );
    public static final DeferredItem<TurbineBladeItem> LOW_PRESSURE_TURBINE_BLADE = ITEMS.register(
        "low_pressure_turbine_blade",
        () -> new TurbineBladeItem(new Item.Properties())
    );
    public static final DeferredItem<Item> LOW_PRESSURE_TURBINE_CASING = ITEMS.register(
        "low_pressure_turbine_casing",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> HIGH_PRESSURE_TURBINE_CASING = ITEMS.register(
        "high_pressure_turbine_casing",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> TARBIN_END = ITEMS.register(
        "tarbin_end",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> LOW_PRESSURE_TURBINE_END = ITEMS.register(
        "low_pressure_turbine_end",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<net.createplant.item.TurbineUnitItem> HIGH_PRESSURE_TURBINE_UNIT = ITEMS.register(
        "high_pressure_turbine_unit", () -> new net.createplant.item.TurbineUnitItem(new Item.Properties(), net.createplant.block.TurbineUnit.Stage.HIGH));
    public static final DeferredItem<net.createplant.item.TurbineUnitItem> LOW_PRESSURE_TURBINE_UNIT = ITEMS.register(
        "low_pressure_turbine_unit", () -> new net.createplant.item.TurbineUnitItem(new Item.Properties(), net.createplant.block.TurbineUnit.Stage.LOW));
    public static final DeferredItem<Item> IRON_CATALYST = ITEMS.register(
        "iron_catalyst",
        () -> new Item(catalyst())
    );
    /** Nitrogen fertilisers for the 3 x 3 crops around the clicked one: ammonium nitrate grows each as far as two bone meals. */
    public static final DeferredItem<Item> AMMONIUM_NITRATE = ITEMS.register(
        "ammonium_nitrate",
        () -> new net.createplant.item.FertilizerItem(2, new Item.Properties())
    );
    public static final DeferredItem<Item> AMMONIUM_SULFATE = ITEMS.register(
        "ammonium_sulfate",
        () -> new net.createplant.item.FertilizerItem(1, new Item.Properties())
    );
    public static final DeferredItem<Item> SULFUR_DUST = ITEMS.register(
        "sulfur_dust",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> ALUMINUM_INGOT = ITEMS.register(
        "aluminum_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> NICKEL_INGOT = ITEMS.register(
        "nickel_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> TITANIUM_INGOT = ITEMS.register(
        "titanium_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> ZIRCONIUM_INGOT = ITEMS.register(
        "zirconium_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> CHROMIUM_INGOT = ITEMS.register(
        "chromium_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> FERROCHROME = ITEMS.registerSimpleItem("ferrochrome");
    public static final DeferredItem<Item> MOLYBDENUM_INGOT = ITEMS.register(
        "molybdenum_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> CARBON_POWDER = ITEMS.registerSimpleItem("carbon_powder");
    public static final DeferredItem<Item> UNPROCESSED_STEEL = ITEMS.registerSimpleItem("unprocessed_steel");
    public static final DeferredItem<Item> PIG_IRON = ITEMS.registerSimpleItem("pig_iron");
    public static final DeferredItem<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> HEATED_UNFINISHED_STEEL = ITEMS.register("heated_unfinished_steel",
        () -> new com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem(new Item.Properties().fireResistant()));
    public static final DeferredItem<Item> STEEL_SLAG = ITEMS.registerSimpleItem("steel_slag");
    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.registerSimpleItem("steel_ingot");
    public static final DeferredItem<Item> STAINLESS_STEEL_INGOT = ITEMS.register(
        "stainless_steel_ingot",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> STAINLESS_STEEL_SHEET = ITEMS.register(
        "stainless_steel_sheet",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> ZIRCALOY_CLADDING_TUBE = ITEMS.register(
        "zircaloy_cladding_tube",
        () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<GasBucketItem> HYDROGEN_GAS_BUCKET = ITEMS.register(
        "hydrogen_gas_bucket",
        () -> new GasBucketItem(CEFluids.HYDROGEN_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> OXYGEN_GAS_BUCKET = ITEMS.register(
        "oxygen_gas_bucket",
        () -> new GasBucketItem(CEFluids.OXYGEN_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> NITROGEN_GAS_BUCKET = ITEMS.register(
        "nitrogen_gas_bucket",
        () -> new GasBucketItem(CEFluids.NITROGEN_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> ARGON_GAS_BUCKET = ITEMS.register(
        "argon_gas_bucket",
        () -> new GasBucketItem(CEFluids.ARGON_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> CONVERTER_GAS_BUCKET = ITEMS.register(
        "converter_gas_bucket",
        () -> new GasBucketItem(CEFluids.CONVERTER_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> AIR_GAS_BUCKET = ITEMS.register(
        "air_gas_bucket",
        () -> new GasBucketItem(CEFluids.AIR_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<BucketItem> LIQUID_OXYGEN_BUCKET = ITEMS.register(
        "liquid_oxygen_bucket",
        () -> new net.createplant.item.ProcessBucketItem(CEFluids.LIQUID_OXYGEN.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> AMMONIA_GAS_BUCKET = ITEMS.register(
        "ammonia_gas_bucket",
        () -> new GasBucketItem(CEFluids.AMMONIA_GAS.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<GasBucketItem> STEAM_BUCKET = ITEMS.register(
        "steam_bucket",
        () -> new GasBucketItem(CEFluids.STEAM.get(), new Item.Properties()
            .craftRemainder(Items.BUCKET)
            .stacksTo(1))
    );
    public static final DeferredItem<BucketItem> NITRIC_ACID_BUCKET = ITEMS.register(
        "nitric_acid_bucket",
        () -> new net.createplant.item.ProcessBucketItem(CEFluids.NITRIC_ACID.get(), CEWorldFluids.bucketProperties())
    );

    /** Own buckets use the standard NeoForge transfer protocol, including gas containers. */
    public static void registerFluidCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        Item[] buckets = ITEMS.getEntries().stream().map(entry -> entry.get())
            .filter(item -> item instanceof BucketItem && !(item instanceof net.createplant.item.ProcessBucketItem)).toArray(Item[]::new);
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
            (stack, context) -> stack.getItem() instanceof net.createplant.item.GasBucketItem ? new net.createplant.item.GasBucketWrapper(stack) : new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack), buckets);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
