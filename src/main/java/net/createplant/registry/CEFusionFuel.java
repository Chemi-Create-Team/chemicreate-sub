package net.createplant.registry;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.block.CryogenicColdBoxBlock;
import net.createplant.block.entity.CryogenicColdBoxBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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

public final class CEFusionFuel {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEAVY_WATER = liquid("heavy_water", 0xCC7FACC3, 1105);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEPLETED_WATER = liquid("deuterium_depleted_water", 0xCC718FA4, 1000);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HELIUM_FEED = new PlantFluid(FLUID_TYPES, FLUIDS, "helium_recovery_feed", 0x99B4B399,
        () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + ".helium_recovery_feed").density(-1).temperature(293).canDrown(false).supportsBoating(false), fluid -> fluid.bucket(CEFusionFuel.HELIUM_FEED_BUCKET)).source;
    public static final DeferredItem<net.createplant.item.GasBucketItem> HELIUM_FEED_BUCKET = ITEMS.register("helium_recovery_feed_bucket",
        () -> new net.createplant.item.GasBucketItem(HELIUM_FEED.get(), new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));
    public static final DeferredItem<Item> LITHIUM_TARGET = ITEMS.registerSimpleItem("lithium_target_rod", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> IRRADIATED_TARGET = ITEMS.registerSimpleItem("irradiated_lithium_target_rod", new Item.Properties().stacksTo(1));
    public static final DeferredBlock<CryogenicColdBoxBlock> COLD_BOX = BLOCKS.registerBlock("cryogenic_cold_box_drive", CryogenicColdBoxBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<Block> REGENERATOR = BLOCKS.registerBlock("cryogenic_regenerator", Block::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    static { ITEMS.registerSimpleBlockItem(COLD_BOX);ITEMS.registerSimpleBlockItem(REGENERATOR); }
    public static final Supplier<BlockEntityType<CryogenicColdBoxBlockEntity>> COLD_BOX_ENTITY = TYPES.register("cryogenic_cold_box",
        () -> BlockEntityType.Builder.of(CryogenicColdBoxBlockEntity::new, COLD_BOX.get()).build(null));

    private CEFusionFuel() { }
    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> liquid(String id, int color, int density) {
        return new PlantFluid(FLUID_TYPES, FLUIDS, id, color, () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + "." + id).density(density).temperature(293), UnaryOperator.identity()).source;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);
        bus.addListener(CryogenicColdBoxBlockEntity::registerCapabilities);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerItem(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
            (stack, context) -> new net.createplant.item.GasBucketWrapper(stack), HELIUM_FEED_BUCKET.get()));
    }
}
