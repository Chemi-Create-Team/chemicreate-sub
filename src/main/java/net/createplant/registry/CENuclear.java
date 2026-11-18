package net.createplant.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.mojang.serialization.Codec;
import net.createplant.CreatePlant;
import net.createplant.block.ControlRodControllerBlock;
import net.createplant.block.ControlRodLinkBlock;
import net.createplant.block.FeedBundleBlock;
import net.createplant.block.FuelChannelBlock;
import net.createplant.block.FuelPoolBlock;
import net.createplant.block.GraphiteModeratorBlock;
import net.createplant.block.ReactorCapBlock;
import net.createplant.block.ReactorConsoleDisplayBlock;
import net.createplant.block.ReactorControlConsoleBlock;
import net.createplant.block.ShieldDriveBlock;
import net.createplant.block.ShieldPortBlock;
import net.createplant.block.VaultBlock;
import net.createplant.block.entity.ControlRodControllerBlockEntity;
import net.createplant.block.entity.ControlRodLinkBlockEntity;
import net.createplant.block.entity.FeedBundleBlockEntity;
import net.createplant.block.entity.FuelChannelBlockEntity;
import net.createplant.block.entity.RodCapBlockEntity;
import net.createplant.block.entity.ReactorConsoleDisplayBlockEntity;
import net.createplant.block.entity.ReactorControlConsoleBlockEntity;
import net.createplant.block.entity.FuelPoolBlockEntity;
import net.createplant.block.entity.ShieldDriveBlockEntity;
import net.createplant.block.entity.ShieldPortBlockEntity;
import net.createplant.item.ProtectiveClothingItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The RBMK reactor: graphite core with four-position fuel/control channel bundles, integrated top-cap rod drives and the
 * serpentinite biological shield around it.
 */
public final class CENuclear {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreatePlant.MOD_ID);

    public static final DeferredBlock<GraphiteModeratorBlock> GRAPHITE = BLOCKS.registerBlock("reactor_graphite", GraphiteModeratorBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_BLOCK).sound(SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<FuelChannelBlock> FUEL_CHANNEL = BLOCKS.registerBlock("reactor_fuel_channel", FuelChannelBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.COAL_BLOCK).sound(SoundType.NETHERITE_BLOCK));
    /** The upper pressure tube around the two-metre shield plug and twelve-metre tube-bundle section. */
    public static final DeferredBlock<VaultBlock> FUEL_CHANNEL_TUBE = BLOCKS.registerBlock("reactor_fuel_channel_tube", VaultBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredBlock<Block> SERPENTINITE = BLOCKS.registerSimpleBlock("serpentinite", BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
    /** Serpentinite in steel plate: its bound water and its mass stop the neutrons and gamma rays of the core. */
    public static final DeferredBlock<VaultBlock> BIOLOGICAL_SHIELD = BLOCKS.registerBlock("biological_shield", VaultBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1200).sound(SoundType.NETHERITE_BLOCK));

    /** The parts of the shield that let rotation and coolant through it to the core inside. */
    public static final DeferredBlock<ShieldDriveBlock> SHIELD_DRIVE = BLOCKS.registerBlock("biological_shield_drive", ShieldDriveBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1200).sound(SoundType.NETHERITE_BLOCK).noOcclusion());
    public static final DeferredBlock<ShieldPortBlock> SHIELD_OUTLET = BLOCKS.registerBlock("biological_shield_coolant_outlet", ShieldPortBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1200).sound(SoundType.NETHERITE_BLOCK));
    /** Thin feed-water tubes under the fuel columns; an exposed bundle accepts a Create fluid pipe directly. */
    public static final DeferredBlock<FeedBundleBlock> FEED_BUNDLE = BLOCKS.registerBlock("reactor_feed_bundle", FeedBundleBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<ControlRodLinkBlock> ROD_LINK = BLOCKS.registerBlock("control_rod_link", ControlRodLinkBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredBlock<ControlRodControllerBlock> ROD_CONTROLLER = BLOCKS.registerBlock("control_rod_controller", ControlRodControllerBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.LEVER).noOcclusion().forceSolidOn());
    public static final DeferredBlock<ReactorControlConsoleBlock> REACTOR_CONTROL_CONSOLE = BLOCKS.registerBlock("reactor_control_console", ReactorControlConsoleBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<ReactorConsoleDisplayBlock> REACTOR_CONSOLE_DISPLAY = BLOCKS.registerBlock("reactor_console_display", ReactorConsoleDisplayBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredBlock<FuelPoolBlock> FUEL_POOL = BLOCKS.registerBlock("fuel_pool", FuelPoolBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.NETHERITE_BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorCapBlock> TOP_CAP = BLOCKS.registerBlock("reactor_top_cap", ReactorCapBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1200).sound(SoundType.NETHERITE_BLOCK));

    public static final DeferredItem<BlockItem> GRAPHITE_ITEM = ITEMS.registerSimpleBlockItem(GRAPHITE);
    public static final DeferredItem<BlockItem> FUEL_CHANNEL_ITEM = ITEMS.registerSimpleBlockItem(FUEL_CHANNEL);
    public static final DeferredItem<BlockItem> FUEL_CHANNEL_TUBE_ITEM = ITEMS.registerSimpleBlockItem(FUEL_CHANNEL_TUBE);
    public static final DeferredItem<BlockItem> SERPENTINITE_ITEM = ITEMS.registerSimpleBlockItem(SERPENTINITE);
    public static final DeferredItem<BlockItem> BIOLOGICAL_SHIELD_ITEM = ITEMS.registerSimpleBlockItem(BIOLOGICAL_SHIELD);
    public static final DeferredItem<BlockItem> SHIELD_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(SHIELD_DRIVE);
    public static final DeferredItem<BlockItem> SHIELD_OUTLET_ITEM = ITEMS.registerSimpleBlockItem(SHIELD_OUTLET);
    public static final DeferredItem<BlockItem> TOP_CAP_ITEM = ITEMS.registerSimpleBlockItem(TOP_CAP);
    public static final DeferredItem<BlockItem> FEED_BUNDLE_ITEM = ITEMS.registerSimpleBlockItem(FEED_BUNDLE);
    public static final DeferredItem<BlockItem> ROD_LINK_ITEM = ITEMS.registerSimpleBlockItem(ROD_LINK);
    public static final DeferredItem<BlockItem> ROD_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(ROD_CONTROLLER);
    public static final DeferredItem<BlockItem> REACTOR_CONTROL_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(REACTOR_CONTROL_CONSOLE);
    public static final DeferredItem<BlockItem> REACTOR_CONSOLE_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(REACTOR_CONSOLE_DISPLAY);
    public static final DeferredItem<BlockItem> FUEL_POOL_ITEM = ITEMS.registerSimpleBlockItem(FUEL_POOL);
    /** A fuel assembly: seven metres of fuel, a two-metre shield plug and a twelve-metre suspension rod. */
    public static final DeferredItem<Item> FUEL_ROD = ITEMS.registerSimpleItem("fuel_rod");
    public static final DeferredItem<Item> COMPACT_ENRICHED_FUEL_ROD = ITEMS.registerSimpleItem("compact_enriched_fuel_rod");
    public static final DeferredItem<Item> SPENT_FUEL_ROD = ITEMS.registerSimpleItem("spent_fuel_rod");
    public static boolean isFuel(net.minecraft.world.item.ItemStack stack) {
        return stack.is(FUEL_ROD.get()) || stack.is(CEFuelCycle.MOX_FUEL_ROD.get()) || stack.is(COMPACT_ENRICHED_FUEL_ROD.get());
    }
    /** A control rod: 7 m absorber, 1.25 m connector and 4.5 m graphite displacer, driven from its top cap. */
    public static final DeferredItem<Item> CONTROL_ROD = ITEMS.registerSimpleItem("control_rod");
    public static final DeferredItem<Item> SPENT_CONTROL_ROD = ITEMS.registerSimpleItem("spent_control_rod");

    /** The hall workers' cap, white coat, trousers and shoe covers: together they intercept 90% of beta exposure and surface contamination; not gamma radiation. */
    private static final Holder<ArmorMaterial> PROTECTIVE_CLOTHING_MATERIAL = ARMOR_MATERIALS.register("protective_clothing", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.BOOTS, 1, ArmorItem.Type.LEGGINGS, 1, ArmorItem.Type.CHESTPLATE, 2, ArmorItem.Type.HELMET, 1, ArmorItem.Type.BODY, 2),
        15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.WHITE_WOOL),
        List.of(new ArmorMaterial.Layer(CreatePlant.location("protective_clothing"))), 0, 0));
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_CAP = clothing("protective_cap", ArmorItem.Type.HELMET, 0.15);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_COAT = clothing("protective_coat", ArmorItem.Type.CHESTPLATE, 0.4);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_TROUSERS = clothing("protective_trousers", ArmorItem.Type.LEGGINGS, 0.25);
    public static final DeferredItem<ProtectiveClothingItem> PROTECTIVE_SHOE_COVERS = clothing("protective_shoe_covers", ArmorItem.Type.BOOTS, 0.1);

    public static final Supplier<BlockEntityType<FuelChannelBlockEntity>> FUEL_CHANNEL_ENTITY = TYPES.register("reactor_fuel_channel",
        () -> BlockEntityType.Builder.of(FuelChannelBlockEntity::new, FUEL_CHANNEL.get()).build(null));
    public static final Supplier<BlockEntityType<FeedBundleBlockEntity>> FEED_BUNDLE_ENTITY = TYPES.register("reactor_feed_bundle",
        () -> BlockEntityType.Builder.of(FeedBundleBlockEntity::new, FEED_BUNDLE.get()).build(null));
    public static final Supplier<BlockEntityType<ShieldDriveBlockEntity>> SHIELD_DRIVE_ENTITY = TYPES.register("biological_shield_drive",
        () -> BlockEntityType.Builder.of(ShieldDriveBlockEntity::new, SHIELD_DRIVE.get()).build(null));
    public static final Supplier<BlockEntityType<ShieldPortBlockEntity>> SHIELD_PORT_ENTITY = TYPES.register("biological_shield_coolant_port",
        () -> BlockEntityType.Builder.of(ShieldPortBlockEntity::new, SHIELD_OUTLET.get()).build(null));
    public static final Supplier<BlockEntityType<FuelPoolBlockEntity>> FUEL_POOL_ENTITY = TYPES.register("fuel_pool",
        () -> BlockEntityType.Builder.of(FuelPoolBlockEntity::new, FUEL_POOL.get()).build(null));
    public static final Supplier<BlockEntityType<RodCapBlockEntity>> TOP_CAP_ENTITY = TYPES.register("reactor_top_cap",
        () -> BlockEntityType.Builder.of(RodCapBlockEntity::new, TOP_CAP.get()).build(null));
    public static final Supplier<BlockEntityType<ControlRodLinkBlockEntity>> ROD_LINK_ENTITY = TYPES.register("control_rod_link",
        () -> BlockEntityType.Builder.of(ControlRodLinkBlockEntity::new, ROD_LINK.get()).build(null));
    public static final Supplier<BlockEntityType<ControlRodControllerBlockEntity>> ROD_CONTROLLER_ENTITY = TYPES.register("control_rod_controller",
        () -> BlockEntityType.Builder.of(ControlRodControllerBlockEntity::new, ROD_CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<ReactorControlConsoleBlockEntity>> REACTOR_CONTROL_CONSOLE_ENTITY = TYPES.register("reactor_control_console",
        () -> BlockEntityType.Builder.of(ReactorControlConsoleBlockEntity::new, REACTOR_CONTROL_CONSOLE.get()).build(null));
    public static final Supplier<BlockEntityType<ReactorConsoleDisplayBlockEntity>> REACTOR_CONSOLE_DISPLAY_ENTITY = TYPES.register("reactor_console_display",
        () -> BlockEntityType.Builder.of(ReactorConsoleDisplayBlockEntity::new, REACTOR_CONSOLE_DISPLAY.get()).build(null));

    /** Radiation dose a player has taken, in sieverts, and the stage of sickness it has brought on; both end with death. */
    public static final Supplier<AttachmentType<Double>> DOSE = ATTACHMENTS.register("radiation_dose",
        () -> AttachmentType.builder(() -> 0d).serialize(Codec.DOUBLE).build());
    public static final Supplier<AttachmentType<Integer>> SICKNESS_STAGE = ATTACHMENTS.register("radiation_sickness_stage",
        () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private CENuclear() { }

    private static DeferredItem<ProtectiveClothingItem> clothing(String name, ArmorItem.Type type, double protection) {
        return ITEMS.register(name, () -> new ProtectiveClothingItem(PROTECTIVE_CLOTHING_MATERIAL, type, protection, new Item.Properties().durability(type.getDurability(5))));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ARMOR_MATERIALS.register(bus);
        TYPES.register(bus);
        ATTACHMENTS.register(bus);
        bus.addListener(FuelChannelBlockEntity::registerCapabilities);
        bus.addListener(FeedBundleBlock::registerCapabilities);
        bus.addListener(ShieldPortBlockEntity::registerCapabilities);
        bus.addListener(FuelPoolBlockEntity::registerCapabilities);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.createplant.nuclear.ReactorCores::levelTick);
        CERadiation.register(bus);
    }
}
