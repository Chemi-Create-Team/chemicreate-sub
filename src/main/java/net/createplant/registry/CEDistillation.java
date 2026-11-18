package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.FractionatingColumnBlock;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CEDistillation {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<FractionatingColumnBlock> COLUMN=BLOCKS.registerBlock("fractionating_column",FractionatingColumnBlock::new,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<FractionatingColumnBlockEntity>> COLUMN_ENTITY=TYPES.register("fractionating_column",()->BlockEntityType.Builder.of(FractionatingColumnBlockEntity::new,COLUMN.get()).build(null));
    public static void register(IEventBus bus) { ITEMS.registerSimpleBlockItem(COLUMN);BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus); }
}
