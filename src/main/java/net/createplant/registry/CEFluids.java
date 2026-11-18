package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class CEFluids {
    private static final DeferredRegister<FluidType> FLUID_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    private static final DeferredRegister.Blocks FLUID_BLOCKS =
        DeferredRegister.createBlocks(CreatePlant.MOD_ID);

    private static final PlantFluid HYDROGEN = new PlantFluid(FLUID_TYPES, FLUIDS, "hydrogen_gas", 0x88DCEEFF,
        () -> gas(-10, 15, 0.04D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.HYDROGEN_GAS_BLOCK).bucket(CEItems.HYDROGEN_GAS_BUCKET)
            .slopeFindDistance(8).levelDecreasePerBlock(1).tickRate(2));
    public static final DeferredHolder<FluidType, FluidType> HYDROGEN_GAS_TYPE = HYDROGEN.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN_GAS = HYDROGEN.source;
    private static final DeferredBlock<LiquidBlock> HYDROGEN_GAS_BLOCK = block("hydrogen_gas", HYDROGEN);

    private static final PlantFluid OXYGEN = new PlantFluid(FLUID_TYPES, FLUIDS, "oxygen_gas", 0x99E8F7FF,
        () -> gas(1, 20, 0.02D),
        fluid -> fluid.block(CEFluids.OXYGEN_GAS_BLOCK).bucket(CEItems.OXYGEN_GAS_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(4));
    public static final DeferredHolder<FluidType, FluidType> OXYGEN_GAS_TYPE = OXYGEN.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> OXYGEN_GAS = OXYGEN.source;
    private static final DeferredBlock<LiquidBlock> OXYGEN_GAS_BLOCK = block("oxygen_gas", OXYGEN);

    private static final PlantFluid NITROGEN = new PlantFluid(FLUID_TYPES, FLUIDS, "nitrogen_gas", 0x88C8D8FF,
        () -> gas(1, 20, 0.02D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.NITROGEN_GAS_BLOCK).bucket(CEItems.NITROGEN_GAS_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(4));
    public static final DeferredHolder<FluidType, FluidType> NITROGEN_GAS_TYPE = NITROGEN.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NITROGEN_GAS = NITROGEN.source;
    private static final DeferredBlock<LiquidBlock> NITROGEN_GAS_BLOCK = block("nitrogen_gas", NITROGEN);

    private static final PlantFluid ARGON = new PlantFluid(FLUID_TYPES, FLUIDS, "argon_gas", 0x88D8C8F0,
        () -> gas(5, 22, 0.02D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.ARGON_GAS_BLOCK).bucket(CEItems.ARGON_GAS_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(4));
    public static final DeferredHolder<FluidType, FluidType> ARGON_GAS_TYPE = ARGON.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ARGON_GAS = ARGON.source;
    private static final DeferredBlock<LiquidBlock> ARGON_GAS_BLOCK = block("argon_gas", ARGON);

    private static final PlantFluid CONVERTER = new PlantFluid(FLUID_TYPES, FLUIDS, "converter_gas", 0x99B7BCAF,
        () -> gas(1, 20, 0.02D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.CONVERTER_GAS_BLOCK).bucket(CEItems.CONVERTER_GAS_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(4));
    public static final DeferredHolder<FluidType, FluidType> CONVERTER_GAS_TYPE = CONVERTER.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CONVERTER_GAS = CONVERTER.source;
    private static final DeferredBlock<LiquidBlock> CONVERTER_GAS_BLOCK = block("converter_gas", CONVERTER);

    private static final PlantFluid AIR = new PlantFluid(FLUID_TYPES, FLUIDS, "air_gas", 0x66D6E4F0,
        () -> gas(3, 20, 0.02D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.AIR_GAS_BLOCK).bucket(CEItems.AIR_GAS_BUCKET)
            .slopeFindDistance(6).levelDecreasePerBlock(1).tickRate(4));
    public static final DeferredHolder<FluidType, FluidType> AIR_GAS_TYPE = AIR.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> AIR_GAS = AIR.source;
    private static final DeferredBlock<LiquidBlock> AIR_GAS_BLOCK = block("air_gas", AIR);

    private static final PlantFluid LIQUID_OXYGEN_FLUID = new PlantFluid(FLUID_TYPES, FLUIDS, "liquid_oxygen", 0xCCA8C8E8,
        () -> FluidType.Properties.create().density(1000).viscosity(950).temperature(90).supportsBoating(false),
        fluid -> fluid.bucket(CEItems.LIQUID_OXYGEN_BUCKET)
            .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5));
    public static final DeferredHolder<FluidType, FluidType> LIQUID_OXYGEN_TYPE = LIQUID_OXYGEN_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_OXYGEN = LIQUID_OXYGEN_FLUID.source;

    private static final PlantFluid AMMONIA = new PlantFluid(FLUID_TYPES, FLUIDS, "ammonia_gas", 0xAAEFD57A,
        () -> gas(-4, 18, 0.03D).canPushEntity(false),
        fluid -> fluid.block(CEFluids.AMMONIA_GAS_BLOCK).bucket(CEItems.AMMONIA_GAS_BUCKET)
            .slopeFindDistance(7).levelDecreasePerBlock(1).tickRate(3));
    public static final DeferredHolder<FluidType, FluidType> AMMONIA_GAS_TYPE = AMMONIA.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> AMMONIA_GAS = AMMONIA.source;
    private static final DeferredBlock<LiquidBlock> AMMONIA_GAS_BLOCK = block("ammonia_gas", AMMONIA);

    private static final PlantFluid STEAM_FLUID = new PlantFluid(FLUID_TYPES, FLUIDS, "steam", 0xAAE9F4FF,
        () -> gas(-20, 10, 0.05D).temperature(373).canPushEntity(false),
        fluid -> fluid.block(CEFluids.STEAM_BLOCK).bucket(CEItems.STEAM_BUCKET)
            .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(2));
    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = STEAM_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> STEAM = STEAM_FLUID.source;
    private static final DeferredBlock<LiquidBlock> STEAM_BLOCK = block("steam", STEAM_FLUID);

    private static final PlantFluid NITRIC_ACID_FLUID = new PlantFluid(FLUID_TYPES, FLUIDS, "nitric_acid", 0xDDE6E54A,
        () -> FluidType.Properties.create().density(1500).viscosity(1200).supportsBoating(false),
        fluid -> fluid.bucket(CEItems.NITRIC_ACID_BUCKET)
            .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5));
    public static final DeferredHolder<FluidType, FluidType> NITRIC_ACID_TYPE = NITRIC_ACID_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NITRIC_ACID = NITRIC_ACID_FLUID.source;

    private static FluidType.Properties gas(int density, int viscosity, double motionScale) {
        return FluidType.Properties.create().density(density).viscosity(viscosity).motionScale(motionScale)
            .canDrown(false).canSwim(false).supportsBoating(false);
    }

    private static DeferredBlock<LiquidBlock> block(String name, PlantFluid fluid) {
        return FLUID_BLOCKS.register(name, () -> new LiquidBlock(fluid.source.get(), BlockBehaviour.Properties.of()
            .noCollission().strength(100.0F).liquid().replaceable()));
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        FLUID_BLOCKS.register(modEventBus);
    }
}
