package net.createplant.registry;

import java.util.function.UnaryOperator;
import net.createplant.CreatePlant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.registries.*;

/** Process fluids shared by the refinery and chemical equipment. */
public final class CEProcessFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreatePlant.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, CreatePlant.MOD_ID);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PREHEATED_CRUDE_OIL = stream("preheated_crude_oil");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COMPRESSED_AIR = stream("compressed_air");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PRESSURISED_HYDROGEN = stream("pressurised_hydrogen");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COMPRESSED_HYDROGEN = stream("compressed_hydrogen");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SYNTHESIS_GAS = stream("synthesis_gas");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WATER_GAS = stream("water_gas");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COMPRESSED_SYNTHESIS_FEED = stream("compressed_synthesis_feed");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_SYNTHESIS_MIXTURE = stream("hot_synthesis_mixture");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COLD_SYNTHESIS_MIXTURE = stream("cold_synthesis_mixture");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HIGH_PRESSURE_STEAM = stream("high_pressure_steam");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> SUPERHEATED_STEAM = stream("superheated_steam");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PRESSURISED_WATER = stream("pressurised_water");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_REACTOR_COOLANT = stream("hot_reactor_coolant");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WARM_WATER = stream("warm_water");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> WASTEWATER = stream("wastewater");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_NITRIC_OXIDE_MIXTURE = stream("hot_nitric_oxide_mixture");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COLD_NITRIC_OXIDE_MIXTURE = stream("cold_nitric_oxide_mixture");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_OLEFINS = stream("hot_olefins");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COOLED_OLEFINS = stream("cooled_olefins");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MOLTEN_POLYETHYLENE = stream("molten_polyethylene");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MOLTEN_POLYPROPYLENE = stream("molten_polypropylene");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_HYDROTREATER_EFFLUENT = stream("hot_hydrotreater_effluent");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COOLED_HYDROTREATER_EFFLUENT = stream("cooled_hydrotreater_effluent");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LOW_SULFUR_DIESEL = stream("low_sulfur_diesel");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN_SULFIDE = stream("hydrogen_sulfide");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_AMMONIA = stream("liquid_ammonia");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_NITROGEN = stream("liquid_nitrogen");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_SULFUR_DIOXIDE = stream("liquid_sulfur_dioxide");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_VINYL_CHLORIDE = stream("liquid_vinyl_chloride");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_PROPYLENE = stream("liquid_propylene");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHYLENE_OXIDE = stream("ethylene_oxide");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHYLENE_GLYCOL = stream("ethylene_glycol");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ANTIFREEZE = stream("antifreeze");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CARBON_MONOXIDE = stream("carbon_monoxide");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CARBON_DIOXIDE = stream("carbon_dioxide");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HOT_HYDROCRACKER_EFFLUENT = stream("hot_hydrocracker_effluent");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> COOLED_HYDROCRACKER_EFFLUENT = stream("cooled_hydrocracker_effluent");
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TRIMETHYL_PHOSPHITE = stream("trimethyl_phosphite", 0xFFC7C4AC);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> CRUDE_METHYLPHOSPHONATE = stream("crude_methylphosphonate", 0xFF8F748C);
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DIMETHYL_METHYLPHOSPHONATE = stream("dimethyl_methylphosphonate", 0xFFBDD6D7);
    private CEProcessFluids() { }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); }
    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name) {
        int color = name.contains("crude") ? 0xFF382E24 : name.contains("naphtha") ? 0xFFCAC3A0
            : name.contains("molten_") ? 0xFFDBD8BC : name.contains("diesel") || name.contains("hydrotreater") ? 0xFFD9C77E
            : name.contains("heavy_oil") ? 0xFF3A3026 : name.contains("hydrocracker") ? 0xFFB9A05A
            : name.equals("wastewater") ? 0xFF6F7357 : name.equals("liquid_nitrogen") ? 0xBBD4E4EA
            : name.equals("antifreeze") ? 0xEE5FD38A : name.contains("glycol") ? 0xEEE6EEF0 : 0xBBD3DFDB;
        return stream(name, color);
    }
    private static DeferredHolder<Fluid, BaseFlowingFluid.Source> stream(String name, int color) {
        return new PlantFluid(TYPES, FLUIDS, name, color, () -> FluidType.Properties.create()
            .descriptionId("fluid." + CreatePlant.MOD_ID + "." + name).supportsBoating(false)
            .temperature(net.createplant.transport.FluidHazards.named(name).kelvin())
            .density(net.createplant.transport.FluidHazards.named(name).gas()?-1:name.equals("liquid_nitrogen")?808:1000), UnaryOperator.identity()).source;
    }
}
