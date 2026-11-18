package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.ReciprocatingCylinderBlock;
import net.createplant.block.ReciprocatingShaftBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.block.entity.ReciprocatingCylinderBlockEntity;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The reciprocating compressor: a cylinder, the steam-engine-style drive that stands on it, and the shaft the drive takes over. */
public final class CEReciprocating {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<ReciprocatingCylinderBlock> CYLINDER = BLOCKS.registerBlock("reciprocating_cylinder", ReciprocatingCylinderBlock::new, properties());
    public static final DeferredBlock<ReciprocatingCompressorBlock> COMPRESSOR = BLOCKS.registerBlock("reciprocating_compressor", ReciprocatingCompressorBlock::new, properties());
    public static final DeferredBlock<ReciprocatingShaftBlock> SHAFT = BLOCKS.registerBlock("reciprocating_drive_shaft", ReciprocatingShaftBlock::new, properties());
    public static final Supplier<BlockEntityType<ReciprocatingCylinderBlockEntity>> CYLINDER_ENTITY = TYPES.register("reciprocating_cylinder",
        () -> BlockEntityType.Builder.of(ReciprocatingCylinderBlockEntity::new, CYLINDER.get()).build(null));
    public static final Supplier<BlockEntityType<ReciprocatingCompressorBlockEntity>> COMPRESSOR_ENTITY = TYPES.register("reciprocating_compressor",
        () -> BlockEntityType.Builder.of(ReciprocatingCompressorBlockEntity::new, COMPRESSOR.get()).build(null));
    public static final Supplier<BlockEntityType<ReciprocatingShaftBlockEntity>> SHAFT_ENTITY = TYPES.register("reciprocating_drive_shaft",
        () -> BlockEntityType.Builder.of(ReciprocatingShaftBlockEntity::new, SHAFT.get()).build(null));

    private CEReciprocating() { }

    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }

    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(CYLINDER);
        ITEMS.registerSimpleBlockItem(COMPRESSOR);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(ReciprocatingCylinderBlockEntity::registerCapabilities);
    }
}
