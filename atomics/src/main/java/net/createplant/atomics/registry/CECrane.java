package net.createplant.atomics.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.CraneDriveBlock;
import net.createplant.atomics.block.entity.CraneDriveBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Gantry cranes on Create metal-girder rails: the service crane drives. */
public final class CECrane {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<CraneDriveBlock> DRIVE = BLOCKS.registerBlock("crane_drive", properties -> new CraneDriveBlock(properties, false),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<CraneDriveBlock> FUSION_DRIVE = BLOCKS.registerBlock("fusion_crane_drive", properties -> new CraneDriveBlock(properties, true),
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DRIVE_ITEM = ITEMS.registerSimpleBlockItem(DRIVE);
    public static final DeferredItem<BlockItem> FUSION_DRIVE_ITEM = ITEMS.registerSimpleBlockItem(FUSION_DRIVE);
    public static final Supplier<BlockEntityType<CraneDriveBlockEntity>> DRIVE_ENTITY = TYPES.register("crane_drive",
        () -> BlockEntityType.Builder.of(CraneDriveBlockEntity::new, DRIVE.get(), FUSION_DRIVE.get()).build(null));

    private CECrane() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(CraneDriveBlockEntity::registerCapabilities);
    }
}
