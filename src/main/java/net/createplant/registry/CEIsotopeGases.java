package net.createplant.registry;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.block.CryogenicColdBoxBlock;
import net.createplant.block.entity.CryogenicColdBoxBlockEntity;
import net.createplant.item.GasBucketItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
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

/** Deuterium, tritium, helium and heavy water with the cryogenic cold box that separates them, shared by the plant and the fusion addon. */
public final class CEIsotopeGases {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks FLUID_BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);

    private static final PlantFluid DEUTERIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "deuterium_gas", 0x99A7DFFF,
        () -> gasProperties(0, 293),
        fluid -> fluid.block(CEIsotopeGases.DEUTERIUM_BLOCK).bucket(CEIsotopeGases.DEUTERIUM_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(3));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEUTERIUM_GAS = DEUTERIUM.source;
    private static final PlantFluid TRITIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "tritium_gas", 0x99D6B56B,
        () -> gasProperties(0, 293),
        fluid -> fluid.block(CEIsotopeGases.TRITIUM_BLOCK).bucket(CEIsotopeGases.TRITIUM_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(3));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TRITIUM_GAS = TRITIUM.source;
    private static final PlantFluid HELIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "helium_gas", 0x88E8DFA7,
        () -> gasProperties(-1, 293),
        fluid -> fluid.block(CEIsotopeGases.HELIUM_BLOCK).bucket(CEIsotopeGases.HELIUM_BUCKET)
            .slopeFindDistance(8).levelDecreasePerBlock(1).tickRate(2));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HELIUM_GAS = HELIUM.source;

    private static final DeferredBlock<LiquidBlock> DEUTERIUM_BLOCK = fluidBlock("deuterium_gas", DEUTERIUM);
    private static final DeferredBlock<LiquidBlock> TRITIUM_BLOCK = fluidBlock("tritium_gas", TRITIUM);
    private static final DeferredBlock<LiquidBlock> HELIUM_BLOCK = fluidBlock("helium_gas", HELIUM);

    public static final DeferredItem<GasBucketItem> DEUTERIUM_BUCKET = ITEMS.register("deuterium_gas_bucket",
        () -> new GasBucketItem(DEUTERIUM_GAS.get(), bucket()));
    public static final DeferredItem<GasBucketItem> TRITIUM_BUCKET = ITEMS.register("tritium_gas_bucket",
        () -> new GasBucketItem(TRITIUM_GAS.get(), bucket()));
    public static final DeferredItem<GasBucketItem> HELIUM_BUCKET = ITEMS.register("helium_gas_bucket",
        () -> new GasBucketItem(HELIUM_GAS.get(), bucket()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEAVY_WATER = liquid("heavy_water", 0xCC7FACC3, 1105);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEPLETED_WATER = liquid("deuterium_depleted_water", 0xCC718FA4, 1000);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HELIUM_FEED = new PlantFluid(FLUID_TYPES, FLUIDS, "helium_recovery_feed", 0x99B4B399,
        () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + ".helium_recovery_feed").density(-1).temperature(293).canDrown(false).supportsBoating(false), fluid -> fluid.bucket(CEIsotopeGases.HELIUM_FEED_BUCKET)).source;
    public static final DeferredItem<GasBucketItem> HELIUM_FEED_BUCKET = ITEMS.register("helium_recovery_feed_bucket",
        () -> new GasBucketItem(HELIUM_FEED.get(), new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));
    public static final DeferredBlock<CryogenicColdBoxBlock> COLD_BOX = BLOCKS.registerBlock("cryogenic_cold_box_drive", CryogenicColdBoxBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<Block> REGENERATOR = BLOCKS.registerBlock("cryogenic_regenerator", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    static { ITEMS.registerSimpleBlockItem(COLD_BOX);ITEMS.registerSimpleBlockItem(REGENERATOR); }
    public static final Supplier<BlockEntityType<CryogenicColdBoxBlockEntity>> COLD_BOX_ENTITY = TYPES.register("cryogenic_cold_box",
        () -> BlockEntityType.Builder.of(CryogenicColdBoxBlockEntity::new, COLD_BOX.get()).build(null));

    private CEIsotopeGases() { }

    private static FluidType.Properties gasProperties(int density, int temperature) {
        return FluidType.Properties.create().density(density).viscosity(12).temperature(temperature)
            .motionScale(.03).canDrown(false).canSwim(false).supportsBoating(false);
    }

    private static DeferredBlock<LiquidBlock> fluidBlock(String id, PlantFluid fluid) {
        return FLUID_BLOCKS.register(id, () -> new LiquidBlock(fluid.source.get(), BlockBehaviour.Properties.of()
            .noCollission().strength(100).liquid().replaceable()));
    }

    private static Item.Properties bucket() { return new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1); }

    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> liquid(String id, int color, int density) {
        return new PlantFluid(FLUID_TYPES, FLUIDS, id, color, () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + "." + id).density(density).temperature(293), UnaryOperator.identity()).source;
    }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus); FLUIDS.register(bus); FLUID_BLOCKS.register(bus);
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus);
        bus.addListener(CryogenicColdBoxBlockEntity::registerCapabilities);
        bus.addListener(CEIsotopeGases::registerBucketCapabilities);
    }

    private static void registerBucketCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        Item[] buckets = {DEUTERIUM_BUCKET.get(), TRITIUM_BUCKET.get(), HELIUM_BUCKET.get()};
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
            (stack, context) -> stack.getItem() instanceof GasBucketItem ? new net.createplant.item.GasBucketWrapper(stack) : new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack), buckets);
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
            (stack, context) -> new net.createplant.item.GasBucketWrapper(stack), HELIUM_FEED_BUCKET.get());
    }
}
