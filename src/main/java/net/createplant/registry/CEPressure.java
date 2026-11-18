package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.*;
import net.createplant.block.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class CEPressure {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE,CreatePlant.MOD_ID);
    public static final DeferredBlock<PressureValveBlock> REDUCER=BLOCKS.registerBlock("pressure_reducing_valve",PressureValveBlock::new,properties());
    public static final Supplier<BlockEntityType<PressureValveBlockEntity>> VALVE_ENTITY=TYPES.register("pressure_control_valve",()->BlockEntityType.Builder.of(PressureValveBlockEntity::new,REDUCER.get()).build(null));
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }
    public static void register(IEventBus bus) { for(var block:java.util.List.of(REDUCER)) ITEMS.registerSimpleBlockItem(block);BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);bus.addListener(PressureValveBlockEntity::registerCapabilities); }
    private CEPressure() { }
}
