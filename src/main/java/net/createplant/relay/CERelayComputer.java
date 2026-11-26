package net.createplant.relay;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CERelayComputer {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredItem<Item> LARGE_RELAY = ITEMS.registerSimpleItem("large_relay");
    public static final DeferredBlock<RelayRackBlock> RACK = BLOCKS.registerBlock("relay_rack", RelayRackBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).lightLevel(s -> s.getValue(RelayRackBlock.ACTIVE) ? 4 : 0));
    public static final DeferredBlock<RelayComputerBlock> COMPUTER = BLOCKS.registerBlock("relay_computer", RelayComputerBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final Supplier<BlockEntityType<RelayComputerBlockEntity>> COMPUTER_ENTITY = TYPES.register("relay_computer",
        () -> BlockEntityType.Builder.of(RelayComputerBlockEntity::new, COMPUTER.get()).build(null));
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CreatePlant.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent> CLICK =
        SOUNDS.register("relay.click", () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(CreatePlant.location("relay.click")));
    private CERelayComputer() { }
    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(RACK);ITEMS.registerSimpleBlockItem(COMPUTER);
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);SOUNDS.register(bus);
    }
}
