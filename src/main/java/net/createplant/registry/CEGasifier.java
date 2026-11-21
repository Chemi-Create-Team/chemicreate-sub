package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.GasifierBlock;
import net.createplant.block.entity.GasifierBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class CEGasifier {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<GasifierBlock> GASIFIER = BLOCKS.registerBlock("steam_gasifier", GasifierBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4).lightLevel(state -> state.getValue(GasifierBlock.LIT) ? 9 : 0));
    static { ITEMS.registerSimpleBlockItem(GASIFIER); }
    public static final DeferredItem<Item> SLAG = ITEMS.registerSimpleItem("glassy_slag");
    public static final Supplier<BlockEntityType<GasifierBlockEntity>> PART = ENTITIES.register("entrained_gasifier",
        () -> BlockEntityType.Builder.of(GasifierBlockEntity::new, GASIFIER.get()).build(null));
    private CEGasifier() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(CEGasifier::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PART.get(), (be, side) -> net.createplant.mixture.GasMixturePort.both(be.fluidPort(side), be.owner() == null ? be : be.owner()));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PART.get(), GasifierBlockEntity::itemPort);
    }
}
