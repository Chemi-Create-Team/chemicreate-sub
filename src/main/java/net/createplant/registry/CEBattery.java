package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.BatteryChemistry;
import net.createplant.block.BatteryTerminalBlock;
import net.createplant.block.entity.BatteryTerminalBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Lead-acid and lithium-ion battery banks: a box of casings under a terminal that joins the Electro Energetics circuit. */
public final class CEBattery {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    public static final DeferredBlock<Block> LEAD_ACID_CASING = casing("lead_acid_battery_casing");
    public static final DeferredBlock<Block> LITHIUM_ION_CASING = casing("lithium_ion_battery_casing");
    public static final DeferredBlock<BatteryTerminalBlock> LEAD_ACID_TERMINAL = terminal("lead_acid_battery_terminal", BatteryChemistry.LEAD_ACID);
    public static final DeferredBlock<BatteryTerminalBlock> LITHIUM_ION_TERMINAL = terminal("lithium_ion_battery_terminal", BatteryChemistry.LITHIUM_ION);
    public static final Supplier<BlockEntityType<BatteryTerminalBlockEntity>> TERMINAL_ENTITY = TYPES.register("battery_terminal",
        () -> BlockEntityType.Builder.of(BatteryTerminalBlockEntity::new, LEAD_ACID_TERMINAL.get(), LITHIUM_ION_TERMINAL.get()).build(null));
    private CEBattery() { }

    private static DeferredBlock<Block> casing(String name) {
        var block = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static DeferredBlock<BatteryTerminalBlock> terminal(String name, BatteryChemistry chemistry) {
        var block = BLOCKS.registerBlock(name, properties -> new BatteryTerminalBlock(properties, chemistry), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);
        bus.addListener(BatteryTerminalBlockEntity::registerCapabilities);
    }
}
