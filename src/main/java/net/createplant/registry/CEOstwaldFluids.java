package net.createplant.registry;

import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Contained process gases; buckets vent particles rather than placing gas source blocks. */
public final class CEOstwaldFluids {
    private static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);

    private static final PlantFluid NITRIC_OXIDE_FLUID = new PlantFluid(TYPES, FLUIDS, "nitric_oxide", 0xAAB8D4DE,
        () -> gasProperties("nitric_oxide"), fluid -> fluid.bucket(CEItems.NITRIC_OXIDE_BUCKET));
    public static final DeferredHolder<FluidType, FluidType> NITRIC_OXIDE_TYPE = NITRIC_OXIDE_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NITRIC_OXIDE = NITRIC_OXIDE_FLUID.source;
    private static final PlantFluid NITROGEN_DIOXIDE_FLUID = new PlantFluid(TYPES, FLUIDS, "nitrogen_dioxide",
        0xAAA66E48, () -> gasProperties("nitrogen_dioxide"), fluid -> fluid.bucket(CEItems.NITROGEN_DIOXIDE_BUCKET));
    public static final DeferredHolder<FluidType, FluidType> NITROGEN_DIOXIDE_TYPE = NITROGEN_DIOXIDE_FLUID.type;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> NITROGEN_DIOXIDE = NITROGEN_DIOXIDE_FLUID.source;

    private CEOstwaldFluids() { }

    private static FluidType.Properties gasProperties(String name) {
        return FluidType.Properties.create().descriptionId("fluid." + CreatePlant.MOD_ID + "." + name)
            .density(-1).viscosity(20).canDrown(false).canSwim(false).canPushEntity(false).supportsBoating(false);
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
