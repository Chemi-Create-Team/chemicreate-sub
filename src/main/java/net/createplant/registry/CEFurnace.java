package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrolysisFurnacePartBlock;
import net.createplant.block.entity.ElectrolysisFurnaceBlockEntity;
import net.createplant.block.entity.ElectrolysisFurnacePartBlockEntity;
import net.createplant.compat.electro.ElectroDevices;
import net.createplant.compat.electro.FurnaceBusbarBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The molten-salt electrolysis furnace: shell, carbon anode and the busbar that holds the controller and joins the circuit. */
public final class CEFurnace {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<ElectrolysisFurnacePartBlock> SHELL = BLOCKS.registerBlock("electrolysis_furnace_shell", ElectrolysisFurnacePartBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
    public static final DeferredBlock<ElectrolysisFurnacePartBlock> ANODE = BLOCKS.registerBlock("electrolysis_furnace_anode", ElectrolysisFurnacePartBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).lightLevel(state -> state.getValue(ElectrolysisFurnacePartBlock.LIT) ? 9 : 0));
    public static final DeferredBlock<FurnaceBusbarBlock> BUSBAR = BLOCKS.registerBlock("electrolysis_furnace_busbar", FurnaceBusbarBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion());
    static { ITEMS.registerSimpleBlockItem(SHELL);ITEMS.registerSimpleBlockItem(ANODE);ITEMS.registerSimpleBlockItem(BUSBAR); }
    public static final Supplier<BlockEntityType<ElectrolysisFurnacePartBlockEntity>> PART_ENTITY = TYPES.register("electrolysis_furnace_part",
        () -> BlockEntityType.Builder.of(ElectrolysisFurnacePartBlockEntity::new, SHELL.get(), ANODE.get()).build(null));
    public static final Supplier<BlockEntityType<ElectrolysisFurnaceBlockEntity>> CONTROLLER_ENTITY = TYPES.register("electrolysis_furnace",
        () -> BlockEntityType.Builder.of((pos, state) -> new ElectrolysisFurnaceBlockEntity(CEFurnace.CONTROLLER_ENTITY.get(), pos, state), BUSBAR.get()).build(null));
    private CEFurnace() { }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);ElectroDevices.register(bus);
        bus.addListener(ElectrolysisFurnacePartBlockEntity::registerCapabilities);
    }
}
