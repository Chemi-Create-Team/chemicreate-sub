package net.createplant.registry;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.IndustrialDispatchBlock;
import net.createplant.block.entity.IndustrialDispatchBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEIndustrialExpansion {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<IndustrialDispatchBlock> DISPATCH = BLOCKS.registerBlock("industrial_dispatch_board", IndustrialDispatchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).forceSolidOn().noOcclusion().sound(SoundType.COPPER));
    public static final List<DeferredBlock<?>> PARTS = List.of(DISPATCH);
    static { PARTS.forEach(ITEMS::registerSimpleBlockItem); }
    public static final Supplier<BlockEntityType<IndustrialDispatchBlockEntity>> DISPATCH_ENTITY = TYPES.register("industrial_dispatch_board", () -> BlockEntityType.Builder.of(IndustrialDispatchBlockEntity::new, DISPATCH.get()).build(null));
    private CEIndustrialExpansion() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DISPATCH_ENTITY.get(), (be, side) -> be.itemPort());
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DISPATCH_ENTITY.get(), (be, side) -> be.fluidPort());
        });
    }
}
