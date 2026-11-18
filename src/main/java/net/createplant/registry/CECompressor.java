package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.ReliefScrubberBlock;
import net.createplant.block.entity.ReliefScrubberBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** The piped toxic-gas scrubber. */
public final class CECompressor {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<ReliefScrubberBlock> RELIEF_SCRUBBER=block("relief_scrubber",ReliefScrubberBlock::new);
    public static final Supplier<BlockEntityType<ReliefScrubberBlockEntity>> RELIEF_SCRUBBER_ENTITY=TYPES.register("relief_scrubber",()->
        BlockEntityType.Builder.of(ReliefScrubberBlockEntity::new,RELIEF_SCRUBBER.get()).build(null));
    private CECompressor() { }
    /** Solid although not full: flowing water would otherwise wash the scrubber away like a torch. */
    private static <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> block(String id,java.util.function.Function<BlockBehaviour.Properties,B> factory) {
        var block=BLOCKS.registerBlock(id,factory,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion().forceSolidOn());
        ITEMS.registerSimpleBlockItem(block);return block;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener(ReliefScrubberBlockEntity::registerCapabilities);
    }
}
