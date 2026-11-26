package net.createplant.registry;

import com.simibubi.create.api.stress.BlockStressValues;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.arm.ArmRecipes;
import net.createplant.arm.ArmTool;
import net.createplant.block.AssemblyJigBlock;
import net.createplant.block.IndustrialArmBlock;
import net.createplant.block.entity.AssemblyJigBlockEntity;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.createplant.item.ArmToolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The assembly jig, the industrial arm and its five tools; recipes are data under data/&lt;namespace&gt;/assembly. */
public final class CEArm {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);

    public static final DeferredBlock<AssemblyJigBlock> JIG = BLOCKS.registerBlock("assembly_jig", AssemblyJigBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredBlock<IndustrialArmBlock> ARM = BLOCKS.registerBlock("industrial_arm", IndustrialArmBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> JIG_ITEM = ITEMS.registerSimpleBlockItem(JIG);
    public static final DeferredItem<BlockItem> ARM_ITEM = ITEMS.registerSimpleBlockItem(ARM);
    public static final Map<ArmTool, DeferredItem<ArmToolItem>> TOOLS = new EnumMap<>(ArmTool.class);
    public static final DeferredItem<Item> WELDED_IRON_BEAM = ITEMS.registerSimpleItem("welded_iron_beam");
    public static final DeferredItem<Item> MAGNETIC_CORE_MEMORY = ITEMS.registerSimpleItem("magnetic_core_memory");
    /** Core memory parts: Ni-Zn ferrite powder is pressed into rings and fired into cores; copper wire is enamelled with resin. */
    public static final DeferredItem<Item> FERRITE_POWDER = ITEMS.registerSimpleItem("ferrite_powder");
    public static final DeferredItem<Item> UNFIRED_FERRITE_RING = ITEMS.registerSimpleItem("unfired_ferrite_ring");
    public static final DeferredItem<Item> FERRITE_CORE = ITEMS.registerSimpleItem("ferrite_core");
    public static final DeferredItem<Item> ENAMELLED_COPPER_WIRE = ITEMS.registerSimpleItem("enamelled_copper_wire");
    public static final Supplier<BlockEntityType<AssemblyJigBlockEntity>> JIG_ENTITY = TYPES.register("assembly_jig",
        () -> BlockEntityType.Builder.of(AssemblyJigBlockEntity::new, JIG.get()).build(null));
    public static final Supplier<BlockEntityType<IndustrialArmBlockEntity>> ARM_ENTITY = TYPES.register("industrial_arm",
        () -> BlockEntityType.Builder.of(IndustrialArmBlockEntity::new, ARM.get()).build(null));

    static {
        for (ArmTool tool : ArmTool.values()) TOOLS.put(tool, ITEMS.registerItem(tool.id(), properties -> new ArmToolItem(tool, properties)));
    }

    private CEArm() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TYPES.register(bus);
        bus.addListener(CEArm::capabilities);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> BlockStressValues.IMPACTS.register(ARM.get(), () -> 4)));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new ArmRecipes()));
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, JIG_ENTITY.get(), (be, side) -> be.tank);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ARM_ENTITY.get(), (be, side) -> be.tank);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ARM_ENTITY.get(), (be, side) -> be.toolSlot);
    }
}
