package net.createplant.atomics.registry;

import java.util.function.Supplier;
import com.simibubi.create.api.stress.BlockStressValues;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.GasCentrifugeBlock;
import net.createplant.atomics.block.CubicPressBlock;
import net.createplant.atomics.block.entity.GasCentrifugeBlockEntity;
import net.createplant.atomics.block.entity.CubicPressBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEHighStressMachines {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<GasCentrifugeBlock> CENTRIFUGE = BLOCKS.registerBlock("gas_centrifuge", GasCentrifugeBlock::new, properties());
    public static final DeferredBlock<CubicPressBlock> PRESS = BLOCKS.registerBlock("cubic_press", CubicPressBlock::new, properties());
    public static final Supplier<BlockEntityType<GasCentrifugeBlockEntity>> CENTRIFUGE_ENTITY = TYPES.register("gas_centrifuge",
        () -> BlockEntityType.Builder.of(GasCentrifugeBlockEntity::new, CENTRIFUGE.get()).build(null));
    public static final Supplier<BlockEntityType<net.createplant.atomics.block.entity.GasCentrifugePortBlockEntity>> CENTRIFUGE_PORT = TYPES.register("gas_centrifuge_port",
        () -> BlockEntityType.Builder.of(net.createplant.atomics.block.entity.GasCentrifugePortBlockEntity::new, CENTRIFUGE.get()).build(null));
    public static final Supplier<BlockEntityType<CubicPressBlockEntity>> PRESS_ENTITY = TYPES.register("cubic_press",
        () -> BlockEntityType.Builder.of(CubicPressBlockEntity::new, PRESS.get()).build(null));

    private static final DeferredRegister<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType> ARMS =
        DeferredRegister.create(com.simibubi.create.api.registry.CreateRegistries.ARM_INTERACTION_POINT_TYPE, CreatePlant.MOD_ID);
    static { ARMS.register("cubic_press", net.createplant.atomics.compat.CubicPressArmPoint::new); }
    private CEHighStressMachines() { }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }
    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(CENTRIFUGE);ITEMS.registerSimpleBlockItem(PRESS);
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);ARMS.register(bus);
        bus.addListener(GasCentrifugeBlockEntity::registerCapabilities);
        bus.addListener(CubicPressBlockEntity::registerCapabilities);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(CENTRIFUGE.get(), () -> 32);
            BlockStressValues.IMPACTS.register(PRESS.get(), () -> 256);
        }));
    }
}
