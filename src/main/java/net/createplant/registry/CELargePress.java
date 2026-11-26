package net.createplant.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.LargeDepotBlock;
import net.createplant.block.LargeMechanicalPressBlock;
import net.createplant.block.LargePressFrameBlock;
import net.createplant.block.entity.LargeMechanicalPressBlockEntity;
import net.createplant.item.LargePressItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CELargePress {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<LargeMechanicalPressBlock> PRESS = BLOCKS.registerBlock("large_mechanical_press", LargeMechanicalPressBlock::new, properties());
    public static final DeferredBlock<LargeDepotBlock> DEPOT = BLOCKS.registerBlock("large_depot", LargeDepotBlock::new, properties());
    public static final DeferredBlock<LargePressFrameBlock> FRAME = BLOCKS.registerBlock("large_press_frame", LargePressFrameBlock::new, properties().noLootTable());
    public static final DeferredItem<Item> LARGE_IRON_PLATE = ITEMS.registerSimpleItem("large_iron_plate");
    public static final Supplier<BlockEntityType<LargeMechanicalPressBlockEntity>> PRESS_ENTITY = TYPES.register("large_mechanical_press",
        () -> BlockEntityType.Builder.of(LargeMechanicalPressBlockEntity::new, PRESS.get()).build(null));
    public static final Supplier<BlockEntityType<DepotBlockEntity>> DEPOT_ENTITY = TYPES.register("large_depot",
        () -> BlockEntityType.Builder.of(CELargePress::createDepot, DEPOT.get()).build(null));

    private CELargePress() { }

    private static DepotBlockEntity createDepot(BlockPos pos, BlockState state) { return new DepotBlockEntity(DEPOT_ENTITY.get(), pos, state); }

    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(); }

    public static void register(IEventBus bus) {
        ITEMS.registerItem("large_mechanical_press", LargePressItem::new);
        ITEMS.registerSimpleBlockItem(DEPOT);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(CELargePress::capabilities);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> BlockStressValues.IMPACTS.register(PRESS.get(), () -> 72)));
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DEPOT_ENTITY.get(), (be, side) -> depotItems(be));
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            if (!LargePressFrameBlock.exposesDepot(state, side)) return null;
            var center = LargePressFrameBlock.center(level, pos, state);
            return center != null && level.getBlockEntity(center) instanceof DepotBlockEntity depot ? depotItems(depot) : null;
        }, FRAME.get());
    }

    private static IItemHandler depotItems(DepotBlockEntity depot) {
        DepotBehaviour behaviour = depot.getBehaviour(DepotBehaviour.TYPE);
        return behaviour == null ? null : new DepotPort(depot, behaviour.itemHandler);
    }

    private record DepotPort(DepotBlockEntity depot, IItemHandler items) implements IItemHandler {
        @Override public int getSlots() { return items.getSlots(); }
        @Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !depot.isRemoved() && items.isItemValid(slot, stack); }
        @Override public ItemStack getStackInSlot(int slot) { return depot.isRemoved() ? ItemStack.EMPTY : items.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return depot.isRemoved() ? stack : items.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return depot.isRemoved() ? ItemStack.EMPTY : items.extractItem(slot, amount, simulate);
        }
    }
}
