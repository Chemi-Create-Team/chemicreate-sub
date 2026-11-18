package net.createplant.registry;

import net.createplant.CreatePlant;
import net.createplant.block.ElectricPumpBlock;
import net.createplant.block.entity.ElectricPumpBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class CEElectricUtilities {
    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<ElectricPumpBlock> PUMP =
            BLOCKS.registerBlock(
                    "electric_pump",
                    ElectricPumpBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final Supplier<BlockEntityType<ElectricPumpBlockEntity>> PUMP_ENTITY =
            TYPES.register(
                    "electric_pump",
                    () ->
                            BlockEntityType.Builder.of(ElectricPumpBlockEntity::new, PUMP.get())
                                    .build(null));

    static {
        ITEMS.registerSimpleBlockItem(PUMP);
    }

    private CEElectricUtilities() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
    }
}
