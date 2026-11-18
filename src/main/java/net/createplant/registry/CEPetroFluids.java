package net.createplant.registry;

import java.util.function.Supplier;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Native CDG fuels alongside the refinery's distinct chemical fractions. */
public final class CEPetroFluids {
    private static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    public static final Supplier<Fluid> CRUDE_OIL = () -> com.jesz.createdieselgenerators.CDGFluids.CRUDE_OIL.getSource();
    public static final Supplier<Fluid> GASOLINE = () -> com.jesz.createdieselgenerators.CDGFluids.GASOLINE.getSource();
    public static final Supplier<Fluid> DIESEL = () -> com.jesz.createdieselgenerators.CDGFluids.DIESEL.getSource();
    private static final PlantFluid HEAVY_OIL_FLUID = fluid("heavy_oil", false, 980, 5000, 0xFF382F24,
        () -> CEItems.HEAVY_OIL_BUCKET.get());
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HEAVY_OIL = HEAVY_OIL_FLUID.source;
    private static final PlantFluid NAPHTHA_FLUID = fluid("naphtha", false, 730, 600, 0xFFE4D9A2,
        () -> CEItems.NAPHTHA_BUCKET.get());
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NAPHTHA = NAPHTHA_FLUID.source;
    private static final PlantFluid LUBRICATING_OIL_FLUID = fluid("lubricating_oil", false, 880, 4000, 0xFFB68A39,
        () -> CEItems.LUBRICATING_OIL_BUCKET.get());
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LUBRICATING_OIL = LUBRICATING_OIL_FLUID.source;
    private static final PlantFluid ETHYLENE_FLUID = fluid("ethylene", true, -1, 20, 0xAABBDAC8,
        () -> CEItems.ETHYLENE_BUCKET.get());
    public static final DeferredHolder<FluidType, FluidType> ETHYLENE_TYPE = ETHYLENE_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHYLENE = ETHYLENE_FLUID.source;
    private static final PlantFluid PROPYLENE_FLUID = fluid("propylene", true, -1, 20, 0xAACAD2AE,
        () -> CEItems.PROPYLENE_BUCKET.get());
    public static final DeferredHolder<FluidType, FluidType> PROPYLENE_TYPE = PROPYLENE_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PROPYLENE = PROPYLENE_FLUID.source;

    private CEPetroFluids() { }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); }

    private static PlantFluid fluid(String name, boolean gas, int density, int viscosity, int color,
        Supplier<? extends Item> bucket) {
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).density(density).viscosity(viscosity)
            .canDrown(!gas).canSwim(!gas).canPushEntity(!gas).supportsBoating(false), fluid -> fluid.bucket(bucket));
    }
}
