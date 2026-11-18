package net.createplant.registry;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.createplant.block.FusionFluidPortBlock;
import net.createplant.block.FusionControlConsoleBlock;
import net.createplant.block.FusionConsolePanelBlock;
import net.createplant.block.FusionMotorGeneratorBlock;
import net.createplant.block.FusionPartBlock;
import net.createplant.block.FusionViewportBlock;
import net.createplant.block.FusionReactorControllerBlock;
import net.createplant.block.FusionVacuumPumpBlock;
import net.createplant.block.entity.FusionFluidPortBlockEntity;
import net.createplant.block.entity.FusionControlConsoleBlockEntity;
import net.createplant.block.entity.FusionMotorGeneratorBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.FusionVacuumPumpBlockEntity;
import net.createplant.item.GasBucketItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Components and working fluids of the fixed-size D-T tokamak. */
public final class CEFusion {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks FLUID_BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);

    private static final PlantFluid DEUTERIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "deuterium_gas", 0x99A7DFFF,
        () -> gasProperties(0, 293),
        fluid -> fluid.block(CEFusion.DEUTERIUM_BLOCK).bucket(CEFusion.DEUTERIUM_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(3));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEUTERIUM_GAS = DEUTERIUM.source;
    private static final PlantFluid TRITIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "tritium_gas", 0x99D6B56B,
        () -> gasProperties(0, 293),
        fluid -> fluid.block(CEFusion.TRITIUM_BLOCK).bucket(CEFusion.TRITIUM_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(3));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TRITIUM_GAS = TRITIUM.source;
    private static final PlantFluid HELIUM = new PlantFluid(FLUID_TYPES, FLUIDS, "helium_gas", 0x88E8DFA7,
        () -> gasProperties(-1, 293),
        fluid -> fluid.block(CEFusion.HELIUM_BLOCK).bucket(CEFusion.HELIUM_BUCKET)
            .slopeFindDistance(8).levelDecreasePerBlock(1).tickRate(2));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HELIUM_GAS = HELIUM.source;
    private static final PlantFluid LIQUID_HELIUM_FLUID = new PlantFluid(FLUID_TYPES, FLUIDS, "liquid_helium", 0xCC8FE7F2,
        () -> FluidType.Properties.create().density(125).viscosity(4).temperature(4).supportsBoating(false),
        fluid -> fluid.bucket(CEFusion.LIQUID_HELIUM_BUCKET)
            .slopeFindDistance(3).levelDecreasePerBlock(1).tickRate(2));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_HELIUM = LIQUID_HELIUM_FLUID.source;

    private static final DeferredBlock<LiquidBlock> DEUTERIUM_BLOCK = fluidBlock("deuterium_gas", DEUTERIUM);
    private static final DeferredBlock<LiquidBlock> TRITIUM_BLOCK = fluidBlock("tritium_gas", TRITIUM);
    private static final DeferredBlock<LiquidBlock> HELIUM_BLOCK = fluidBlock("helium_gas", HELIUM);

    private static BlockBehaviour.Properties shell() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(12, 1_200).sound(SoundType.NETHERITE_BLOCK);
    }
    private static BlockBehaviour.Properties internals() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1_200).sound(SoundType.COPPER);
    }

    public static final DeferredBlock<FusionPartBlock> REINFORCED_CASING = BLOCKS.registerBlock("fusion_reinforced_casing", FusionPartBlock::new, shell());
    public static final DeferredBlock<FusionPartBlock> TOROIDAL_FIELD_COIL = BLOCKS.registerBlock("fusion_toroidal_field_coil", FusionPartBlock::new, internals());
    public static final DeferredBlock<FusionPartBlock> CENTRAL_SOLENOID = BLOCKS.registerBlock("fusion_central_solenoid", FusionPartBlock::new, internals());
    public static final DeferredBlock<FusionPartBlock> VACUUM_VESSEL = BLOCKS.registerBlock("fusion_vacuum_vessel", FusionPartBlock::new, shell());
    public static final DeferredBlock<FusionViewportBlock> VIEWPORT = BLOCKS.registerBlock("fusion_viewport", FusionViewportBlock::new, shell().noOcclusion());
    public static final DeferredBlock<FusionPartBlock> BREEDING_BLANKET = BLOCKS.registerBlock("fusion_breeding_blanket", FusionPartBlock::new, shell());
    public static final DeferredBlock<FusionPartBlock> DIVERTOR = BLOCKS.registerBlock("fusion_divertor", FusionPartBlock::new, shell());
    public static final DeferredBlock<FusionReactorControllerBlock> CONTROLLER = BLOCKS.registerBlock("fusion_reactor_controller", FusionReactorControllerBlock::new,
        shell().noOcclusion());
    public static final DeferredBlock<FusionMotorGeneratorBlock> MOTOR_GENERATOR = BLOCKS.registerBlock("fusion_motor_generator", FusionMotorGeneratorBlock::new,
        shell().noOcclusion());
    public static final DeferredBlock<FusionVacuumPumpBlock> VACUUM_PUMP = BLOCKS.registerBlock("fusion_vacuum_pump", FusionVacuumPumpBlock::new,
        shell().noOcclusion());
    public static final DeferredBlock<FusionControlConsoleBlock> CONSOLE = BLOCKS.registerBlock("fusion_control_console", FusionControlConsoleBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.COPPER).noOcclusion()
            .lightLevel(state -> state.getValue(FusionControlConsoleBlock.ACTIVE) ? 9 : 0));
    public static final DeferredBlock<FusionConsolePanelBlock> CONSOLE_PANEL = BLOCKS.registerBlock("fusion_console_panel", FusionConsolePanelBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).sound(SoundType.COPPER).noOcclusion()
            .lightLevel(state -> state.getValue(FusionConsolePanelBlock.ACTIVE) ? 9 : 0));

    public static final DeferredBlock<FusionFluidPortBlock> DEUTERIUM_PORT = port("fusion_deuterium_port", FusionFluidPortBlock.Kind.DEUTERIUM);
    public static final DeferredBlock<FusionFluidPortBlock> TRITIUM_PORT = port("fusion_tritium_port", FusionFluidPortBlock.Kind.TRITIUM);
    public static final DeferredBlock<FusionFluidPortBlock> ASH_PORT = port("fusion_ash_port", FusionFluidPortBlock.Kind.ASH);
    public static final DeferredBlock<FusionFluidPortBlock> HELIUM_INLET = port("fusion_helium_inlet", FusionFluidPortBlock.Kind.HELIUM_IN);
    public static final DeferredBlock<FusionFluidPortBlock> HELIUM_OUTLET = port("fusion_helium_outlet", FusionFluidPortBlock.Kind.HELIUM_OUT);
    public static final DeferredBlock<FusionFluidPortBlock> COOLANT_INLET = port("fusion_coolant_inlet", FusionFluidPortBlock.Kind.COOLANT_IN);
    public static final DeferredBlock<FusionFluidPortBlock> COOLANT_OUTLET = port("fusion_coolant_outlet", FusionFluidPortBlock.Kind.COOLANT_OUT);

    private static final List<DeferredBlock<?>> BLOCK_ITEMS = List.of(REINFORCED_CASING, TOROIDAL_FIELD_COIL, CENTRAL_SOLENOID,
        VACUUM_VESSEL, VIEWPORT, BREEDING_BLANKET, DIVERTOR, CONTROLLER, MOTOR_GENERATOR, VACUUM_PUMP, DEUTERIUM_PORT, TRITIUM_PORT, ASH_PORT,
        HELIUM_INLET, HELIUM_OUTLET, COOLANT_INLET, COOLANT_OUTLET, CONSOLE, CONSOLE_PANEL);
    static { for (DeferredBlock<?> block : BLOCK_ITEMS) ITEMS.registerSimpleBlockItem(block); }
    public static List<DeferredBlock<?>> blockItems() { return BLOCK_ITEMS; }

    public static final DeferredItem<Item> LITHIUM_LEAD_BLANKET = ITEMS.register("lithium_lead_blanket_cartridge",
        () -> new Item(new Item.Properties().durability(24_000).setNoRepair().stacksTo(1)));
    public static final DeferredItem<Item> DIVERTOR_CASSETTE = ITEMS.register("tungsten_divertor_cassette",
        () -> new Item(new Item.Properties().durability(16_000).setNoRepair().stacksTo(1)));
    public static final DeferredItem<Item> SPENT_BLANKET = ITEMS.registerSimpleItem("spent_blanket_cartridge");
    public static final DeferredItem<Item> SPENT_DIVERTOR = ITEMS.registerSimpleItem("spent_divertor_cassette");
    /** Gold and titanium held in the blanket in place of lithium-lead: it breeds no tritium, and lasts as long. */
    public static final DeferredItem<Item> IRRADIATION_CARTRIDGE = ITEMS.register("irradiation_cartridge",
        () -> new Item(new Item.Properties().durability(24_000).setNoRepair().stacksTo(1)));
    public static final DeferredItem<Item> IRRADIATED_CARTRIDGE = ITEMS.registerSimpleItem("irradiated_cartridge");
    public static final DeferredItem<GasBucketItem> DEUTERIUM_BUCKET = ITEMS.register("deuterium_gas_bucket",
        () -> new GasBucketItem(DEUTERIUM_GAS.get(), bucket()));
    public static final DeferredItem<GasBucketItem> TRITIUM_BUCKET = ITEMS.register("tritium_gas_bucket",
        () -> new GasBucketItem(TRITIUM_GAS.get(), bucket()));
    public static final DeferredItem<GasBucketItem> HELIUM_BUCKET = ITEMS.register("helium_gas_bucket",
        () -> new GasBucketItem(HELIUM_GAS.get(), bucket()));
    public static final DeferredItem<BucketItem> LIQUID_HELIUM_BUCKET = ITEMS.register("liquid_helium_bucket",
        () -> new net.createplant.item.ProcessBucketItem(LIQUID_HELIUM.get(), bucket()));

    public static final Supplier<BlockEntityType<FusionReactorControllerBlockEntity>> CONTROLLER_ENTITY = TYPES.register("fusion_reactor_controller",
        () -> BlockEntityType.Builder.of(FusionReactorControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final Supplier<BlockEntityType<FusionMotorGeneratorBlockEntity>> MOTOR_GENERATOR_ENTITY = TYPES.register("fusion_motor_generator",
        () -> BlockEntityType.Builder.of(FusionMotorGeneratorBlockEntity::new, MOTOR_GENERATOR.get()).build(null));
    public static final Supplier<BlockEntityType<FusionFluidPortBlockEntity>> PORT_ENTITY = TYPES.register("fusion_fluid_port",
        () -> BlockEntityType.Builder.of(FusionFluidPortBlockEntity::new, DEUTERIUM_PORT.get(), TRITIUM_PORT.get(), ASH_PORT.get(),
            HELIUM_INLET.get(), HELIUM_OUTLET.get(), COOLANT_INLET.get(), COOLANT_OUTLET.get()).build(null));
    public static final Supplier<BlockEntityType<FusionVacuumPumpBlockEntity>> VACUUM_PUMP_ENTITY = TYPES.register("fusion_vacuum_pump",
        () -> BlockEntityType.Builder.of(FusionVacuumPumpBlockEntity::new, VACUUM_PUMP.get()).build(null));
    public static final Supplier<BlockEntityType<FusionControlConsoleBlockEntity>> CONSOLE_ENTITY = TYPES.register("fusion_control_console",
        () -> BlockEntityType.Builder.of(FusionControlConsoleBlockEntity::new, CONSOLE.get()).build(null));

    private CEFusion() { }

    private static DeferredBlock<FusionFluidPortBlock> port(String id, FusionFluidPortBlock.Kind kind) {
        return BLOCKS.registerBlock(id, properties -> new FusionFluidPortBlock(properties, kind), shell().noOcclusion());
    }

    private static FluidType.Properties gasProperties(int density, int temperature) {
        return FluidType.Properties.create().density(density).viscosity(12).temperature(temperature)
            .motionScale(.03).canDrown(false).canSwim(false).supportsBoating(false);
    }

    private static DeferredBlock<LiquidBlock> fluidBlock(String id, PlantFluid fluid) {
        DeferredBlock<LiquidBlock> block = FLUID_BLOCKS.register(id, () -> new LiquidBlock(fluid.source.get(), BlockBehaviour.Properties.of()
            .noCollission().strength(100).liquid().replaceable()));
        return block;
    }

    private static Item.Properties bucket() { return new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1); }

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus); FLUIDS.register(bus); FLUID_BLOCKS.register(bus);
        BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus);
        bus.addListener(FusionFluidPortBlockEntity::registerCapabilities);
        bus.addListener(FusionReactorControllerBlockEntity::registerCapabilities);
        bus.addListener(CEFusion::registerBucketCapabilities);
    }

    private static void registerBucketCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        Item[] buckets = {DEUTERIUM_BUCKET.get(), TRITIUM_BUCKET.get(), HELIUM_BUCKET.get()};
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
            (stack, context) -> stack.getItem() instanceof net.createplant.item.GasBucketItem ? new net.createplant.item.GasBucketWrapper(stack) : new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack), buckets);
    }
}
