package net.createplant.registry;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.agriculture.AgriculturalSprayerMovement;
import net.createplant.block.AgriculturalSprayerBlock;
import net.createplant.block.entity.AgriculturalSprayerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEAgriculture {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<AgriculturalSprayerBlock> SPRAYER = BLOCKS.registerBlock("agricultural_sprayer", AgriculturalSprayerBlock::new,
        BlockBehaviour.Properties.of().strength(2).sound(SoundType.METAL).noOcclusion());
    public static final Supplier<BlockEntityType<AgriculturalSprayerBlockEntity>> SPRAYER_ENTITY =
        ENTITIES.register("agricultural_sprayer", () -> BlockEntityType.Builder.of(AgriculturalSprayerBlockEntity::new, SPRAYER.get()).build(null));

    private CEAgriculture() { }

    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(SPRAYER);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(AgriculturalSprayerBlockEntity::registerCapabilities);
        bus.addListener(CEAgriculture::registerMovement);
    }

    private static void registerMovement(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> MovementBehaviour.REGISTRY.register(SPRAYER.get(), new AgriculturalSprayerMovement()));
    }
}
