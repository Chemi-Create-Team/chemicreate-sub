package net.createplant.atomics.registry;

import net.createplant.registry.PlantFluid;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.ExtractionColumnSectionBlock;
import net.createplant.atomics.block.ExtractionColumnStructureBlock;
import net.createplant.atomics.block.FuelCycleMachineBlock;
import net.createplant.atomics.block.VitrificationCanisterBlock;
import net.createplant.atomics.block.FuelCycleOutletBlock;
import net.createplant.block.ProcessFrameBlock;
import net.createplant.atomics.block.entity.FuelCycleOutletBlockEntity;
import net.createplant.atomics.block.entity.FuelCycleMachineBlockEntity;
import net.createplant.atomics.compat.electro.VitrificationBusbarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CEFuelCycle {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePlant.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreatePlant.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static BlockBehaviour.Properties steel() { return BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(8, 1200).sound(SoundType.NETHERITE_BLOCK).noOcclusion(); }
    public static final DeferredBlock<FuelCycleMachineBlock> EXTRACTION_COLUMN = BLOCKS.registerBlock("pulse_extraction_column", FuelCycleMachineBlock::new, steel());
    public static final DeferredBlock<VitrificationBusbarBlock> MELTER = BLOCKS.registerBlock("vitrification_melter", VitrificationBusbarBlock::new, steel());
    public static final DeferredBlock<ExtractionColumnSectionBlock> COLUMN_SHELL = BLOCKS.registerBlock("extraction_column_shell", ExtractionColumnSectionBlock::new, steel());
    public static final DeferredBlock<ExtractionColumnSectionBlock> PULSE_PLATE = BLOCKS.registerBlock("extraction_pulse_plate", ExtractionColumnSectionBlock::new, steel());
    public static final DeferredBlock<ExtractionColumnStructureBlock> COLUMN_STRUCTURE = BLOCKS.registerBlock("extraction_column_structure", ExtractionColumnStructureBlock::new, steel());
    public static final DeferredBlock<ProcessFrameBlock> MELTER_SHELL = BLOCKS.registerBlock("vitrification_refractory_shell", ProcessFrameBlock::new, steel());
    public static final DeferredBlock<Block> MELTER_ELECTRODE = BLOCKS.registerSimpleBlock("vitrification_electrode", steel());
    public static final DeferredBlock<FuelCycleOutletBlock> EXTRACTION_OUTLET = BLOCKS.registerBlock("extraction_column_outlet", FuelCycleOutletBlock::new, steel());
    public static final DeferredBlock<VitrificationCanisterBlock> EMPTY_CANISTER = BLOCKS.registerBlock("empty_waste_canister", p -> new VitrificationCanisterBlock(p, false), steel());
    public static final DeferredBlock<VitrificationCanisterBlock> WASTE_CANISTER = BLOCKS.registerBlock("vitrified_waste_canister", p -> new VitrificationCanisterBlock(p, true), steel());
    public static final List<DeferredBlock<?>> BLOCK_ITEMS = List.of(EXTRACTION_COLUMN, MELTER, COLUMN_SHELL, PULSE_PLATE, MELTER_SHELL, MELTER_ELECTRODE, EXTRACTION_OUTLET, EMPTY_CANISTER, WASTE_CANISTER);
    static { BLOCK_ITEMS.forEach(ITEMS::registerSimpleBlockItem); }
    public static final DeferredItem<Item> CHOPPED_FUEL = ITEMS.registerSimpleItem("chopped_spent_fuel");
    public static final DeferredItem<Item> CLADDING_HULLS = ITEMS.registerSimpleItem("spent_cladding_hulls");
    public static final DeferredItem<Item> PLUTONIUM_DIOXIDE = ITEMS.registerSimpleItem("plutonium_dioxide");
    public static final DeferredItem<Item> DEPLETED_DIOXIDE = ITEMS.registerSimpleItem("depleted_uranium_dioxide");
    public static final DeferredItem<Item> MOX_POWDER = ITEMS.registerSimpleItem("mox_powder");
    public static final DeferredItem<Item> GREEN_MOX = ITEMS.registerSimpleItem("green_mox_pellet");
    public static final DeferredItem<Item> MOX_PELLET = ITEMS.registerSimpleItem("mox_pellet");
    public static final DeferredItem<Item> MOX_FUEL_ROD = ITEMS.registerSimpleItem("mox_fuel_rod");
    public static final DeferredItem<Item> GLASS_FRIT = ITEMS.registerSimpleItem("borosilicate_glass_frit");
    public static final List<DeferredItem<Item>> MATERIALS = List.of(CHOPPED_FUEL, CLADDING_HULLS, PLUTONIUM_DIOXIDE, DEPLETED_DIOXIDE,
        MOX_POWDER, GREEN_MOX, MOX_PELLET, MOX_FUEL_ROD, GLASS_FRIT);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DISSOLVED_FUEL = stream("dissolved_spent_fuel", 0xDD948349);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> EXTRACTION_SOLVENT = stream("tbp_extraction_solvent", 0xCCD7C599);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PLUTONIUM_NITRATE = stream("plutonium_nitrate_solution", 0xDD8D795D);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HIGH_LEVEL_WASTE = stream("high_level_raffinate", 0xDD76634B);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TRIBUTYL_PHOSPHATE = stream("tributyl_phosphate", 0xCCCECAA8);
    public static final Supplier<BlockEntityType<FuelCycleMachineBlockEntity>> MACHINE_ENTITY = TYPES.register("fuel_cycle_machine",
        () -> BlockEntityType.Builder.of(FuelCycleMachineBlockEntity::new, EXTRACTION_COLUMN.get(), MELTER.get()).build(null));
    public static final Supplier<BlockEntityType<FuelCycleOutletBlockEntity>> OUTLET_ENTITY = TYPES.register("fuel_cycle_outlet",
        () -> BlockEntityType.Builder.of(FuelCycleOutletBlockEntity::new, EXTRACTION_OUTLET.get()).build(null));
    private CEFuelCycle() { }
    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int color) {
        return new PlantFluid(FLUID_TYPES, FLUIDS, name, color, () -> FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + "." + name)
            .temperature(293).density(1100).supportsBoating(false), UnaryOperator.identity()).source;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);FLUID_TYPES.register(bus);FLUIDS.register(bus);bus.addListener(CEFuelCycle::capabilities);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MACHINE_ENTITY.get(), (be, side) -> net.createplant.mixture.GasMixturePort.both(be.fluidPort(side), be));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_ENTITY.get(), (be, side) -> be.itemPort(side));
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> {
            BlockPos centre = ExtractionColumnStructureBlock.centre(level, pos, state);
            return centre == null ? null : level.getCapability(Capabilities.FluidHandler.BLOCK, centre, side);
        }, COLUMN_STRUCTURE.get());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, OUTLET_ENTITY.get(), (be, side) -> {
            var owner = be.owner();return side == null || owner == null ? null : net.createplant.mixture.GasMixturePort.outlet(owner.outlet(be.getBlockState().getValue(FuelCycleOutletBlock.TANK)), owner);
        });
    }
}
