package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.GasPipeBlock;
import net.createplant.block.entity.GasPipeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Native Create transport for the two gas pipe sizes. */
public final class CEGasPipes {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<GasPipeBlock> GAS_PIPE=BLOCKS.registerBlock("gas_pipe",p->new GasPipeBlock(p,false),BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<GasPipeBlock> LARGE_GAS_PIPE=BLOCKS.registerBlock("large_gas_pipe",p->new GasPipeBlock(p,true),BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<GasPipeBlockEntity>> GAS_PIPE_ENTITY=TYPES.register("gas_pipe",()->BlockEntityType.Builder.of(GasPipeBlockEntity::new,GAS_PIPE.get(),LARGE_GAS_PIPE.get()).build(null));
    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(GAS_PIPE);ITEMS.registerSimpleBlockItem(LARGE_GAS_PIPE);
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
    }
}
