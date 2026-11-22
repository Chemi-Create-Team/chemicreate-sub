package net.createplant.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import com.simibubi.create.api.stress.BlockStressValues;
import net.createplant.CreatePlant;
import net.createplant.block.WetProcessDriveBlock;
import net.createplant.block.WetProcessPartBlock;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.createplant.block.entity.WetProcessPartBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

public final class CEWetProcessing {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    public static final Map<String, DeferredHolder<Fluid, BaseFlowingFluid.Source>> SLURRIES = new LinkedHashMap<>();
    public static final Map<WetProcessPartBlock.Role, DeferredBlock<WetProcessPartBlock>> PARTS = new LinkedHashMap<>();
    static {
        for (var role : WetProcessPartBlock.Role.values()) {
            var block = BLOCKS.registerBlock(role.id, p -> new WetProcessPartBlock(role, p), properties());
            PARTS.put(role, block);ITEMS.registerSimpleBlockItem(block);
        }
        slurry("copper_leach_slurry", 0xFF315B57);
        slurry("iron_leach_slurry", 0xFF884A35);
        slurry("zinc_leach_slurry", 0xFF73867A);
        slurry("tin_leach_slurry", 0xFF7A817F);
        slurry("lead_leach_slurry", 0xFF8B8F96);
        slurry("wastewater_slurry", 0xFF716557);
    }
    public static final DeferredBlock<WetProcessDriveBlock> CLARIFIER = BLOCKS.registerBlock("clarifier_center_drive", p -> new WetProcessDriveBlock(p), properties());
    public static final DeferredItem<Item> TAILINGS = ITEMS.registerSimpleItem("tailings_mud");
    public static final DeferredItem<Item> FLOCCULANT = ITEMS.registerSimpleItem("flocculant");
    public static final Supplier<BlockEntityType<WetProcessBlockEntity>> DRIVE_ENTITY = ENTITIES.register("wet_process_drive",
        () -> BlockEntityType.Builder.of(WetProcessBlockEntity::new, CLARIFIER.get()).build(null));
    public static final Supplier<BlockEntityType<WetProcessPartBlockEntity>> PART_ENTITY = ENTITIES.register("wet_process_part",
        () -> BlockEntityType.Builder.of(WetProcessPartBlockEntity::new, PARTS.values().stream().map(Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    static { ITEMS.registerSimpleBlockItem(CLARIFIER); }
    private CEWetProcessing() { }
    private static BlockBehaviour.Properties properties() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4).noOcclusion(); }
    private static void slurry(String name, int color) {
        SLURRIES.put(name, new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create().temperature(293).density(1400)
            .viscosity(3500).supportsBoating(false), p -> p.tickRate(20).slopeFindDistance(2).levelDecreasePerBlock(2)).source);
    }
    public static boolean slurry(Fluid fluid) { return SLURRIES.values().stream().anyMatch(source -> source.get().isSame(fluid)); }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);TYPES.register(bus);FLUIDS.register(bus);
        bus.addListener(CEWetProcessing::capabilities);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(CLARIFIER.get(), () -> 8d);
        }));
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PART_ENTITY.get(), (part, side) -> net.createplant.mixture.GasMixturePort.both(part.fluidPort(side), part.owner()));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PART_ENTITY.get(), WetProcessPartBlockEntity::itemPort);
    }
}
