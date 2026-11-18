package net.createplant.registry;

import java.util.function.Supplier;

import net.createplant.CreatePlant;
import net.createplant.block.ConverterShellBlock;
import net.createplant.block.ConverterTrunnionBearingBlock;
import net.createplant.block.entity.ConverterShellBlockEntity;
import net.createplant.block.entity.ConverterTrunnionBearingBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The fixed ports, powered trunnion and refractory lining of the glued oxygen/AOD converter. */
public final class CEConverter {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<ConverterShellBlock> SHELL = BLOCKS.registerBlock("converter_shell", ConverterShellBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredItem<BlockItem> SHELL_ITEM = ITEMS.registerSimpleBlockItem(SHELL);
    public static final DeferredBlock<ConverterTrunnionBearingBlock> TRUNNION_BEARING = BLOCKS.registerBlock(
        "converter_trunnion_bearing", ConverterTrunnionBearingBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().sound(SoundType.NETHERITE_BLOCK));
    public static final DeferredItem<BlockItem> TRUNNION_BEARING_ITEM = ITEMS.registerSimpleBlockItem(TRUNNION_BEARING);
    public static final DeferredBlock<net.minecraft.world.level.block.Block> REFRACTORY_BRICKS = BLOCKS.registerSimpleBlock(
        "zirconia_refractory_bricks", BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)
            .strength(4, 12).sound(SoundType.DEEPSLATE_BRICKS));
    public static final DeferredItem<BlockItem> REFRACTORY_BRICKS_ITEM = ITEMS.registerSimpleBlockItem(REFRACTORY_BRICKS);
    public static final Supplier<BlockEntityType<ConverterShellBlockEntity>> SHELL_ENTITY = TYPES.register("converter_shell",
        () -> BlockEntityType.Builder.of(ConverterShellBlockEntity::new, SHELL.get()).build(null));
    public static final Supplier<BlockEntityType<ConverterTrunnionBearingBlockEntity>> TRUNNION_ENTITY = TYPES.register(
        "converter_trunnion_bearing",
        () -> BlockEntityType.Builder.of(ConverterTrunnionBearingBlockEntity::new, TRUNNION_BEARING.get()).build(null));

    private CEConverter() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(ConverterShellBlockEntity::registerCapabilities);
    }
}
