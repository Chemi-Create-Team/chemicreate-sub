package net.createplant.registry;

import java.util.List;
import net.createplant.CreatePlant;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CEBuildingMaterials {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    public static final DeferredItem<Item> CLINKER = ITEMS.registerSimpleItem("cement_clinker");
    public static final DeferredItem<Item> GROUND_CLINKER = ITEMS.registerSimpleItem("ground_clinker");
    public static final DeferredItem<Item> CEMENT = ITEMS.registerSimpleItem("cement_powder");
    public static final DeferredBlock<Block> CONCRETE = BLOCKS.registerSimpleBlock("industrial_concrete", concrete());
    public static final DeferredBlock<SlabBlock> CONCRETE_SLAB = BLOCKS.registerBlock("industrial_concrete_slab", SlabBlock::new, concrete());
    public static final DeferredBlock<StairBlock> CONCRETE_STAIRS = BLOCKS.registerBlock("industrial_concrete_stairs",
        properties -> new StairBlock(CONCRETE.get().defaultBlockState(), properties), concrete());
    public static final DeferredBlock<Block> COATED_FLOOR = BLOCKS.registerSimpleBlock("corrosion_resistant_floor", concrete());
    public static final DeferredBlock<SlabBlock> COATED_SLAB = BLOCKS.registerBlock("corrosion_resistant_floor_slab", SlabBlock::new, concrete());
    public static final List<DeferredItem<Item>> MATERIALS = List.of(CLINKER, GROUND_CLINKER, CEMENT);
    public static final List<DeferredItem<BlockItem>> BUILDING_ITEMS = List.of(ITEMS.registerSimpleBlockItem(CONCRETE),
        ITEMS.registerSimpleBlockItem(CONCRETE_SLAB), ITEMS.registerSimpleBlockItem(CONCRETE_STAIRS),
        ITEMS.registerSimpleBlockItem(COATED_FLOOR), ITEMS.registerSimpleBlockItem(COATED_SLAB));

    private CEBuildingMaterials() { }

    private static BlockBehaviour.Properties concrete() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.GRAY_CONCRETE).strength(3, 8);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);
    }
}
