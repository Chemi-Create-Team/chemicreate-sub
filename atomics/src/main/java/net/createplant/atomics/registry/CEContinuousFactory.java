package net.createplant.atomics.registry;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.ContinuousFactoryBlock;
import net.createplant.atomics.block.FactoryPortBlock;
import net.createplant.atomics.block.FactoryStageBlock;
import net.createplant.atomics.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.atomics.contraptions.FactoryBodyEntity;
import net.createplant.atomics.contraptions.FactoryContraption;
import net.createplant.atomics.factory.FactoryKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEContinuousFactory {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<ContraptionType> CONTRAPTIONS = DeferredRegister.create(CreateBuiltInRegistries.CONTRAPTION_TYPE.key(), CreatePlant.MOD_ID);

    public static final DeferredBlock<ContinuousFactoryBlock> KILN = control(FactoryKind.KILN);
    public static final DeferredBlock<ContinuousFactoryBlock> MILL = control(FactoryKind.MILL);
    public static final DeferredBlock<ContinuousFactoryBlock> WINDER = control(FactoryKind.WINDER);
    public static final DeferredBlock<FactoryStageBlock> STAND = BLOCKS.registerBlock("rolling_stand", FactoryStageBlock::new, properties());
    public static final DeferredBlock<FactoryStageBlock> BRAKE = BLOCKS.registerBlock("winder_brake", FactoryStageBlock::new, properties());
    public static final DeferredBlock<FactoryPortBlock> OUTLET = BLOCKS.registerBlock("continuous_factory_outlet", FactoryPortBlock::new, properties());
    public static final DeferredBlock<Block> FRAME = BLOCKS.registerBlock("continuous_factory_frame", Block::new, properties());
    public static final DeferredBlock<Block> KILN_STACK = BLOCKS.registerBlock("rotary_kiln_exhaust", net.createplant.atomics.block.KilnExhaustBlock::new, properties().noOcclusion());
    public static final DeferredItem<Item> SCRAP = ITEMS.registerSimpleItem("deformed_workpiece");
    public static final DeferredItem<Item> LOOSE_WINDING = ITEMS.registerSimpleItem("loose_winding");
    public static final DeferredItem<Item> THICK_STEEL_PLATE = ITEMS.registerSimpleItem("thick_steel_plate");
    public static final DeferredItem<Item> STEEL_H_BEAM = ITEMS.registerSimpleItem("steel_h_beam");
    public static final DeferredItem<Item> STEEL_RAIL_STOCK = ITEMS.registerSimpleItem("steel_rail_stock");
    public static final DeferredItem<Item> COPPER_FOIL = ITEMS.registerSimpleItem("copper_foil");
    public static final DeferredItem<Item> ALUMINUM_FOIL = ITEMS.registerSimpleItem("aluminum_foil");
    public static final DeferredItem<Item> ELECTRICAL_STEEL_SHEET = ITEMS.registerSimpleItem("electrical_steel_sheet");
    public static final DeferredItem<Item> STAINLESS_CLAD_PLATE = ITEMS.registerSimpleItem("stainless_clad_plate");
    public static final DeferredItem<Item> LEAD_SHEET = ITEMS.registerSimpleItem("lead_sheet");
    public static final DeferredItem<Item> GOLD_FOIL = ITEMS.registerSimpleItem("gold_foil");
    public static final DeferredItem<Item> STEEL_CORE_CABLE = ITEMS.registerSimpleItem("steel_core_aluminum_cable");
    public static final DeferredItem<Item> WIRE_ROPE = ITEMS.registerSimpleItem("heavy_wire_rope");

    public static final Supplier<BlockEntityType<ContinuousFactoryBlockEntity>> CONTROLLER_ENTITY = TYPES.register("continuous_factory",
        () -> BlockEntityType.Builder.of(ContinuousFactoryBlockEntity::new, KILN.get(), MILL.get(), WINDER.get()).build(null));
    public static final Supplier<BlockEntityType<KineticBlockEntity>> STAGE_ENTITY = TYPES.register("continuous_factory_drive",
        () -> BlockEntityType.Builder.of(CEContinuousFactory::stage, STAND.get(), BRAKE.get()).build(null));
    public static final Supplier<EntityType<FactoryBodyEntity>> BODY_ENTITY = ENTITIES.register("continuous_factory_body",
        () -> EntityType.Builder.<FactoryBodyEntity>of(FactoryBodyEntity::new, MobCategory.MISC).sized(1, 1).fireImmune().noSave()
            .clientTrackingRange(12).updateInterval(1).build(CreatePlant.MOD_ID + ":continuous_factory_body"));
    public static final Supplier<ContraptionType> BODY_CONTRAPTION = CONTRAPTIONS.register("continuous_factory_body", () -> new ContraptionType(FactoryContraption::new));

    private CEContinuousFactory() { }
    private static KineticBlockEntity stage(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        return new KineticBlockEntity(STAGE_ENTITY.get(), pos, state);
    }
    private static DeferredBlock<ContinuousFactoryBlock> control(FactoryKind kind) {
        return BLOCKS.registerBlock(kind.id, props -> new ContinuousFactoryBlock(props, kind), properties());
    }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).pushReaction(PushReaction.BLOCK); }

    public static void register(IEventBus bus) {
        for (var block : List.of(KILN, MILL, WINDER, STAND, BRAKE, OUTLET, FRAME, KILN_STACK)) ITEMS.registerSimpleBlockItem(block);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        ENTITIES.register(bus);
        CONTRAPTIONS.register(bus);
        bus.addListener(CEContinuousFactory::capabilities);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(KILN.get(), () -> FactoryKind.KILN.stress);
            BlockStressValues.IMPACTS.register(WINDER.get(), () -> FactoryKind.WINDER.stress);
            BlockStressValues.IMPACTS.register(STAND.get(), () -> 32);
            BlockStressValues.IMPACTS.register(BRAKE.get(), () -> 12);
        }));
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CONTROLLER_ENTITY.get(), ContinuousFactoryBlockEntity::inlet);
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, face) -> {
            if (face == state.getValue(FactoryPortBlock.FACING).getOpposite()) return null;
            var controller = FactoryPortBlock.controller(level, pos, state);
            return controller == null ? null : controller.outlet(pos);
        }, OUTLET.get());
    }
}
