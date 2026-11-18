package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.ElectrolysisControllerBlock;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.ElectrolysisCellBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEElectrolysis {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredItem<Item> ELECTRODE_PLATE = ITEMS.registerSimpleItem("asbestos_wrapped_electrode");
    public static final DeferredItem<Item> NICKEL_ELECTRODE_PLATE = ITEMS.registerSimpleItem("nickel_electrode_plate");
    public static final DeferredBlock<ElectrolysisCellBlock> CELL = BLOCKS.registerBlock("electrolysis_cell", ElectrolysisCellBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<ElectrolysisControllerBlock> CONTROLLER = BLOCKS.registerBlock("electrolysis_cell_controller", ElectrolysisControllerBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<ElectrodeHeadBlock> HEAD = BLOCKS.registerBlock("electrolysis_electrode_head", ElectrodeHeadBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<ElectrolysisCellBlockEntity>> CELL_ENTITY = TYPES.register("electrolysis_cell",
        () -> BlockEntityType.Builder.of(ElectrolysisCellBlockEntity::new, CELL.get()).build(null));
    public static final Supplier<BlockEntityType<ElectrolysisControllerBlockEntity>> CONTROLLER_ENTITY = TYPES.register("electrolysis_cell_controller",
        () -> BlockEntityType.Builder.of(ElectrolysisControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<ElectrodeHeadBlockEntity>> HEAD_ENTITY = TYPES.register("electrolysis_electrode_head",
        () -> BlockEntityType.Builder.of(ElectrodeHeadBlockEntity::new, HEAD.get()).build(null));
    static { ITEMS.registerSimpleBlockItem(CELL);ITEMS.registerSimpleBlockItem(CONTROLLER);ITEMS.registerSimpleBlockItem(HEAD); }
    private CEElectrolysis() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);TYPES.register(bus);ITEMS.register(bus);
        bus.addListener(ElectrolysisCellBlockEntity::registerCapabilities);
        bus.addListener(ElectrodeHeadBlockEntity::registerCapabilities);
    }
}
