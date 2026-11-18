package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.FlareTipBlock;
import net.createplant.block.entity.FlareTipBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEFlare {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<FlareTipBlock> TIP=BLOCKS.registerBlock("flare_tip",FlareTipBlock::new,properties().lightLevel(state->state.getValue(FlareTipBlock.PILOT) ? 15 : 0));
    public static final Supplier<BlockEntityType<FlareTipBlockEntity>> TIP_ENTITY=TYPES.register("flare_tip",()->BlockEntityType.Builder.of(FlareTipBlockEntity::new,TIP.get()).build(null));

    private CEFlare() { }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }
    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(TIP);
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener(FlareTipBlockEntity::registerCapabilities);
    }
}
