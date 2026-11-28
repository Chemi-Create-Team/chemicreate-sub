package net.createplant.logistics.registry;

import net.createplant.CreatePlant;
import net.createplant.logistics.block.BlastingChargeBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Industrial explosive: ANFO from ammonium nitrate and diesel, and the blasting charge made from it. */
public final class CEBlasting {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    public static final DeferredItem<Item> ANFO = ITEMS.registerSimpleItem("anfo");
    public static final DeferredBlock<BlastingChargeBlock> BLASTING_CHARGE = BLOCKS.registerBlock("blasting_charge", BlastingChargeBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.TNT).noOcclusion());
    static { ITEMS.registerSimpleBlockItem(BLASTING_CHARGE); }
    private CEBlasting() { }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus); }
}
