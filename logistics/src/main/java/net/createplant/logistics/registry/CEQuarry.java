package net.createplant.logistics.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.logistics.block.QuarryDriveBlock;
import net.createplant.logistics.block.entity.QuarryDriveBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The quarry's corner drive, a gantry on Create metal-girder rails. */
public final class CEQuarry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<QuarryDriveBlock> QUARRY_DRIVE = BLOCKS.registerBlock("quarry_drive", QuarryDriveBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> QUARRY_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(QUARRY_DRIVE);
    public static final Supplier<BlockEntityType<QuarryDriveBlockEntity>> QUARRY_DRIVE_ENTITY = TYPES.register("quarry_drive",
        () -> BlockEntityType.Builder.of((pos, state) -> new QuarryDriveBlockEntity(CEQuarry.QUARRY_DRIVE_ENTITY.get(), pos, state), QUARRY_DRIVE.get()).build(null));

    private CEQuarry() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(QuarryDriveBlockEntity::registerCapabilities);
        bus.addListener(QuarryDriveBlockEntity::registerChunkLoader);
    }
}
