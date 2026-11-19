package net.createplant.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.block.GasDetectorBlock;
import net.createplant.block.GasRecoveryDuctBlock;
import net.createplant.block.entity.GasRecoveryDuctBlockEntity;
import net.createplant.cloud.GasCloud;
import net.createplant.item.GasCylinderItem;
import net.createplant.item.GasMaskItem;
import net.createplant.safety.ToxicGasEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Gas safety equipment and the duct's closed recovery stream. */
public final class CESafety {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CreatePlant.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final Supplier<EntityType<GasCloud>> GAS_CLOUD = ENTITIES.register("gas_cloud", () ->
        EntityType.Builder.<GasCloud>of(GasCloud::new, MobCategory.MISC).sized(1, 1).clientTrackingRange(4).updateInterval(20).build("gas_cloud"));
    public static final Holder<MobEffect> TOXIC_GAS = EFFECTS.register("toxic_gas", ToxicGasEffect::new);
    public static final Holder<MobEffect> ACID_BURN = EFFECTS.register("acid_burn", net.createplant.safety.AcidBurnEffect::new);
    /** Seconds of toxic gas the filter canister takes before it is spent. */
    public static final int FILTER_SECONDS = 1200;
    private static final Holder<ArmorMaterial> GAS_MASK_MATERIAL = ARMOR_MATERIALS.register("gas_mask", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.BOOTS, 0, ArmorItem.Type.LEGGINGS, 0, ArmorItem.Type.CHESTPLATE, 0, ArmorItem.Type.HELMET, 1, ArmorItem.Type.BODY, 0),
        5, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(CreatePlant.location("gas_mask"))), 0, 0));
    public static final DeferredItem<ArmorItem> GAS_MASK = ITEMS.register("gas_mask",
        () -> new GasMaskItem(GAS_MASK_MATERIAL, new Item.Properties().durability(FILTER_SECONDS).setNoRepair()));
    public static final DeferredItem<Item> GAS_MASK_FILTER = ITEMS.registerSimpleItem("gas_mask_filter");
    public static final DeferredBlock<GasDetectorBlock> GAS_DETECTOR = BLOCKS.registerBlock("gas_detector", GasDetectorBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).lightLevel(state -> state.getValue(GasDetectorBlock.POWER) == 0 ? 0 : 5));
    public static final DeferredBlock<GasRecoveryDuctBlock> GAS_RECOVERY_DUCT = BLOCKS.registerBlock("gas_recovery_duct", GasRecoveryDuctBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Supplier<BlockEntityType<GasRecoveryDuctBlockEntity>> GAS_RECOVERY_DUCT_ENTITY = BLOCK_ENTITIES.register("gas_recovery_duct",
        () -> BlockEntityType.Builder.of(GasRecoveryDuctBlockEntity::new, GAS_RECOVERY_DUCT.get()).build(null));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> RAW_PROCESS_GAS = new PlantFluid(FLUID_TYPES, FLUIDS, "raw_process_gas", 0xFFBFCAD0,
        () -> FluidType.Properties.create().supportsBoating(false), UnaryOperator.identity()).source;
    public static final DeferredItem<GasCylinderItem> GAS_CYLINDER = ITEMS.register("inert_gas_cylinder",
        () -> new GasCylinderItem(new Item.Properties().stacksTo(1)));
    public static final Supplier<DataComponentType<SimpleFluidContent>> CYLINDER_CONTENTS = COMPONENTS.register("cylinder_contents",
        () -> DataComponentType.<SimpleFluidContent>builder().persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());

    private CESafety() { }
    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(GAS_DETECTOR);
        ITEMS.registerSimpleBlockItem(GAS_RECOVERY_DUCT);
        BLOCKS.register(bus);ITEMS.register(bus);COMPONENTS.register(bus);EFFECTS.register(bus);ARMOR_MATERIALS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        bus.addListener(CESafety::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, context) -> GasCylinderItem.handler(stack), GAS_CYLINDER.get());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GAS_RECOVERY_DUCT_ENTITY.get(), GasRecoveryDuctBlockEntity::port);
    }
}
