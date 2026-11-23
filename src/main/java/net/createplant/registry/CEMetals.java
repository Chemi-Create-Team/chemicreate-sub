package net.createplant.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Ores of the seven engineering metals and the minerals, oxides and sulfate liquors between ore and metal. Each ore has a stone
 * and a deepslate form and drops its mineral; the existing engineering ingots are in {@link CEItems}.
 */
public final class CEMetals {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);

    /** Ore name to the mineral it drops. */
    public static final Map<String, DeferredItem<Item>> MINERALS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<BlockItem>> ORES = new LinkedHashMap<>();
    static {
        for (String ore : new String[] { "bauxite", "pentlandite", "ilmenite", "zircon", "chromite", "molybdenite", "cassiterite", "spodumene", "galena" }) {
            MINERALS.put(ore, ITEMS.registerSimpleItem(ore));
            ORES.put(ore + "_ore", ore(ore + "_ore", Blocks.IRON_ORE));
            ORES.put("deepslate_" + ore + "_ore", ore("deepslate_" + ore + "_ore", Blocks.DEEPSLATE_IRON_ORE));
        }
    }
    /** Nine-ingot blocks; stainless steel and titanium also serve as Deep Seas pressure hulls (see compat/deepseas). */
    public static final DeferredItem<BlockItem> STEEL_BLOCK = metalBlock("steel_block");
    public static final DeferredItem<BlockItem> STAINLESS_STEEL_BLOCK = metalBlock("stainless_steel_block");
    public static final DeferredItem<BlockItem> TITANIUM_BLOCK = metalBlock("titanium_block");
    public static final DeferredItem<Item> ALUMINA = ITEMS.registerSimpleItem("alumina");
    /** Pressed plates: aluminium fins and jackets, titanium blades, nickel electrodes. */
    public static final DeferredItem<Item> ALUMINUM_SHEET = ITEMS.registerSimpleItem("aluminum_sheet");
    public static final DeferredItem<Item> TITANIUM_SHEET = ITEMS.registerSimpleItem("titanium_sheet");
    public static final DeferredItem<Item> NICKEL_SHEET = ITEMS.registerSimpleItem("nickel_sheet");
    public static final DeferredItem<Item> TITANIUM_DIOXIDE = ITEMS.registerSimpleItem("titanium_dioxide");
    public static final DeferredItem<Item> ZIRCONIA = ITEMS.registerSimpleItem("zirconia");
    public static final DeferredItem<Item> NICKEL_OXIDE = ITEMS.registerSimpleItem("nickel_oxide");
    public static final DeferredItem<Item> MOLYBDENUM_TRIOXIDE = ITEMS.registerSimpleItem("molybdenum_trioxide");
    public static final DeferredItem<Item> CHROMIUM_OXIDE = ITEMS.registerSimpleItem("chromium_oxide");
    public static final DeferredItem<Item> IRON_OXIDE_RED = ITEMS.registerSimpleItem("iron_oxide_red");
    public static final DeferredItem<Item> NICKEL_TITANIUM_YELLOW = ITEMS.registerSimpleItem("nickel_titanium_yellow");
    public static final DeferredItem<Item> MOLYBDENUM_BLUE = ITEMS.registerSimpleItem("molybdenum_blue");

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ALUMINIUM_SULFATE_SOLUTION = stream("aluminium_sulfate_solution", 0xEEDCE4E8);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TITANYL_SULFATE_SOLUTION = stream("titanyl_sulfate_solution", 0xEEC6C890);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ZIRCONIUM_SULFATE_SOLUTION = stream("zirconium_sulfate_solution", 0xEEE2DACA);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> IRON_SULFATE_SOLUTION = stream("iron_sulfate_solution", 0xEEA9C48C);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COPPER_SULFATE_SOLUTION = stream("copper_sulfate_solution", 0xEE3D86D2);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ZINC_SULFATE_SOLUTION = stream("zinc_sulfate_solution", 0xEED6E2DE);
    public static final DeferredItem<Item> TIN_INGOT = ITEMS.registerSimpleItem("tin_ingot");
    public static final DeferredItem<Item> LEAD_INGOT = ITEMS.registerSimpleItem("lead_ingot");
    public static final DeferredItem<Item> LEAD_SINTER = ITEMS.registerSimpleItem("lead_sinter");
    public static final DeferredItem<Item> LITHIUM_CARBONATE = ITEMS.registerSimpleItem("lithium_carbonate");
    public static final DeferredItem<Item> CRUSHED_TIN = ITEMS.registerSimpleItem("crushed_raw_tin");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TIN_SULFATE_SOLUTION = stream("tin_sulfate_solution", 0xEEC9D9DB);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LEAD_CHLORIDE_SOLUTION = stream("lead_chloride_solution", 0xEEE6E2D2);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DILUTE_SULFURIC_ACID = stream("dilute_sulfuric_acid", 0xEED4DB96);

    private CEMetals() { }

    private static DeferredItem<BlockItem> metalBlock(String name) {
        return ITEMS.registerSimpleBlockItem(BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
    }

    private static DeferredItem<BlockItem> ore(String name, net.minecraft.world.level.block.Block like) {
        DeferredBlock<DropExperienceBlock> block = BLOCKS.registerBlock(name, properties -> new DropExperienceBlock(UniformInt.of(1, 3), properties),
            BlockBehaviour.Properties.ofFullCopy(like));
        return ITEMS.registerSimpleBlockItem(block);
    }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int color) {
        var hazard = FluidHazards.named(name);
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).supportsBoating(false)
            .temperature((int) Math.round(hazard.kelvin())).density(hazard.gas() ? -1 : 1000), UnaryOperator.identity()).source;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
