package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.FuelCellBlock;
import net.createplant.block.entity.FuelCellBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The alkaline fuel cell: hydrogen and oxygen into Electro Energetics power and warm water. */
public final class CEFuelCell {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<FuelCellBlock> FUEL_CELL = BLOCKS.registerBlock("fuel_cell", FuelCellBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    static { ITEMS.registerSimpleBlockItem(FUEL_CELL); }
    public static final Supplier<BlockEntityType<FuelCellBlockEntity>> FUEL_CELL_ENTITY = TYPES.register("fuel_cell",
        () -> BlockEntityType.Builder.of(FuelCellBlockEntity::new, FUEL_CELL.get()).build(null));
    private CEFuelCell() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener(FuelCellBlockEntity::registerCapabilities);
    }
}
