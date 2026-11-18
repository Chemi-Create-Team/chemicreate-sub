package net.createplant.registry;

import net.createplant.CreatePlant;
import net.createplant.mixture.MixtureContents;
import net.createplant.mixture.MixtureFluid;
import net.createplant.mixture.MixtureData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.*;

/** A transport-only carrier; its components, rather than placed source blocks, own its contents. */
public final class CEMixtures {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePlant.MOD_ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MixtureContents>> MIXTURE_CONTENTS = COMPONENTS.register("mixture_contents",
        () -> DataComponentType.<MixtureContents>builder().persistent(MixtureContents.CODEC).networkSynchronized(MixtureContents.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MixtureData>> MIXTURE_DATA = COMPONENTS.register("mixture_component_data",
        () -> DataComponentType.<MixtureData>builder().persistent(MixtureData.CODEC).networkSynchronized(MixtureData.STREAM_CODEC).build());
    public static final DeferredHolder<FluidType, FluidType> TYPE = TYPES.register("chemical_mixture", () -> new FluidType(FluidType.Properties.create()
        .descriptionId("fluid." + CreatePlant.MOD_ID + ".chemical_mixture").density(1000).viscosity(1000).canConvertToSource(false)) { });
    public static final DeferredHolder<Fluid, MixtureFluid.Source> CHEMICAL_MIXTURE = FLUIDS.register("chemical_mixture", () -> new MixtureFluid.Source(properties()));
    public static final DeferredHolder<Fluid, MixtureFluid.Flowing> FLOWING_MIXTURE = FLUIDS.register("flowing_chemical_mixture", () -> new MixtureFluid.Flowing(properties()));
    private CEMixtures() { }
    private static BaseFlowingFluid.Properties properties() { return new BaseFlowingFluid.Properties(TYPE, CHEMICAL_MIXTURE, FLOWING_MIXTURE); }
    public static void register(IEventBus bus) { COMPONENTS.register(bus);TYPES.register(bus);FLUIDS.register(bus); }
}
