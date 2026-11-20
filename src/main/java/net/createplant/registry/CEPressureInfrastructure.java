package net.createplant.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.HighPressurePumpBlock;
import net.createplant.block.HoseConnectorBlock;
import net.createplant.block.PressurePumpCasingBlock;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createplant.block.entity.HoseConnectorBlockEntity;
import net.createplant.block.entity.PressurePumpPortBlockEntity;
import net.createplant.item.FlexibleRubberHoseItem;
import net.createplant.item.HazardSuitItem;
import net.createplant.item.HighPressurePumpItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public final class CEPressureInfrastructure {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, CreatePlant.MOD_ID);

    public static final DeferredBlock<HighPressurePumpBlock> PUMP = BLOCKS.register("high_pressure_pump",
        () -> new HighPressurePumpBlock(properties(), true));
    public static final DeferredBlock<HighPressurePumpBlock> INTAKE = BLOCKS.register("pressure_pump_intake",
        () -> new HighPressurePumpBlock(properties(), false));
    public static final DeferredBlock<PressurePumpCasingBlock> CASING = BLOCKS.registerBlock("pressure_pump_casing", PressurePumpCasingBlock::new, properties());
    public static final DeferredBlock<HoseConnectorBlock> CONNECTOR = BLOCKS.registerBlock("hose_connector", HoseConnectorBlock::new, properties().noOcclusion());
    public static final Supplier<BlockEntityType<HighPressurePumpBlockEntity>> PUMP_ENTITY = ENTITIES.register("high_pressure_pump",
        () -> BlockEntityType.Builder.of(HighPressurePumpBlockEntity::new, PUMP.get(), INTAKE.get()).build(null));
    public static final Supplier<BlockEntityType<PressurePumpPortBlockEntity>> PUMP_PORT_ENTITY = ENTITIES.register("pressure_pump_port",
        () -> BlockEntityType.Builder.of(PressurePumpPortBlockEntity::new, CASING.get()).build(null));
    public static final Supplier<BlockEntityType<HoseConnectorBlockEntity>> CONNECTOR_ENTITY = ENTITIES.register("hose_connector",
        () -> BlockEntityType.Builder.of(HoseConnectorBlockEntity::new, CONNECTOR.get()).build(null));

    public static final DeferredItem<HighPressurePumpItem> PUMP_ITEM = ITEMS.register("high_pressure_pump", () -> new HighPressurePumpItem(PUMP.get(), new Item.Properties()));
    public static final DeferredItem<FlexibleRubberHoseItem> HOSE = ITEMS.register("flexible_rubber_hose", () -> new FlexibleRubberHoseItem(new Item.Properties()));
    private static final Holder<ArmorMaterial> HAZARD = MATERIALS.register("chemical_hazard", () -> new ArmorMaterial(
        Map.of(ArmorItem.Type.BOOTS, 2, ArmorItem.Type.LEGGINGS, 3, ArmorItem.Type.CHESTPLATE, 4, ArmorItem.Type.HELMET, 2, ArmorItem.Type.BODY, 0),
        8, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY, List.of(new ArmorMaterial.Layer(CreatePlant.location("chemical_hazard"))), 0, 0));
    public static final DeferredItem<HazardSuitItem> HELMET = armor("hazard_helmet", ArmorItem.Type.HELMET, 1200);
    public static final DeferredItem<HazardSuitItem> CHEST = armor("hazard_chestplate", ArmorItem.Type.CHESTPLATE, 280);
    public static final DeferredItem<HazardSuitItem> LEGS = armor("hazard_leggings", ArmorItem.Type.LEGGINGS, 260);
    public static final DeferredItem<HazardSuitItem> BOOTS = armor("hazard_boots", ArmorItem.Type.BOOTS, 220);

    private CEPressureInfrastructure() { }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK);
    }

    private static DeferredItem<HazardSuitItem> armor(String id, ArmorItem.Type slot, int durability) {
        return ITEMS.register(id, () -> new HazardSuitItem(HAZARD, slot, new Item.Properties().durability(durability).setNoRepair()));
    }

    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(CONNECTOR);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MATERIALS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(CEPressureInfrastructure::stress);
        bus.addListener(CEPressureInfrastructure::capabilities);
    }

    private static void stress(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(PUMP.get(), () -> 8.0));
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK, PressurePumpCasingBlock::port, CASING.get());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CONNECTOR_ENTITY.get(), HoseConnectorBlockEntity::port);
    }
}
