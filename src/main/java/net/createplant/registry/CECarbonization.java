package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.CharredWoodBlock;
import net.createplant.block.entity.CharredWoodBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CECarbonization {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<CharredWoodBlock> CHARRED_WOOD = BLOCKS.registerBlock("charred_wood", CharredWoodBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG));
    public static final Supplier<BlockEntityType<CharredWoodBlockEntity>> CHARRED_WOOD_ENTITY = TYPES.register("charred_wood",
        () -> BlockEntityType.Builder.of(CharredWoodBlockEntity::new, CHARRED_WOOD.get()).build(null));
    static { ITEMS.registerSimpleBlockItem(CHARRED_WOOD); }
    private CECarbonization() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
    }
}
