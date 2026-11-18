package net.createplant.thermal;

import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed reference data for chemical quantities and fraction order; no vessel or phase solver. */
public final class FluidMaterials {
    public static final double R=8.314462618,ATMOSPHERE=101325;
    private static final Map<String,double[]> TABLE=Map.ofEntries(
        entry("minecraft:water",18.015,373.15),entry("minecraft:lava",60.08,2503),
        entry("neoforge:milk",18.015,373.7),entry("create:tea",18.015,373.15),
        entry("create:honey",18.015,383.15),entry("create:chocolate",18.015,374.15),
        entry("create:potion",18.015,373.15),
        entry("heavy_water",20.0276,374.55),entry("deuterium_depleted_water",18.015,373.15),entry("helium_recovery_feed",10.005,30),
        entry("warm_water",18.015,373.15),entry("steam",18.015,373.15),
        entry("hydrogen_gas",2.016,20.28),entry("deuterium_gas",4.028,23.67),
        entry("tritium_gas",6.032,25.04),entry("helium_gas",4.0026,4.22),
        entry("liquid_helium",4.0026,4.22),entry("oxygen_gas",31.998,90.19),
        entry("nitrogen_gas",28.014,77.36),entry("liquid_nitrogen",28.014,77.36),entry("ammonia_gas",17.031,239.82),
        entry("air_gas",28.96,78.8),entry("argon_gas",39.948,87.3),entry("liquid_oxygen",31.998,90.19),
        entry("converter_gas",28.01,81.6),entry("carbon_monoxide",28.0101,81.638),
        // CO2 uses its one-atmosphere sublimation point; no solid phase or liquid stream is registered.
        entry("carbon_dioxide",44.0095,194.67),
        entry("nitric_oxide",30.006,121.4),entry("nitrogen_dioxide",46.006,294.3),
        entry("nitric_acid",63.01,356),
        entry("ethylene",28.054,169.4),entry("propylene",42.081,225.4),entry("liquid_propylene",42.081,225.4),// Process mixtures are pseudo-pure components from Kay's rule on their main constituents.
        entry("synthesis_gas",8.5155,34.5),
        entry("water_gas",15.01305,50.959),
        entry("compressed_synthesis_feed",8.5155,34.5),
        entry("hot_synthesis_mixture",9.79,65.3),entry("cold_synthesis_mixture",9.79,65.3),
        entry("hot_nitric_oxide_mixture",28.9,80),entry("cold_nitric_oxide_mixture",28.9,80),
        entry("naphtha",110,400),entry("hot_olefins",28.5,175),entry("cooled_olefins",63,300),
        entry("molten_polyethylene",1000,900),entry("molten_polypropylene",1000,900),
        entry("hot_hydrotreater_effluent",200,520),
        entry("cooled_hydrotreater_effluent",200,520),entry("low_sulfur_diesel",200,520),
        entry("hot_hydrocracker_effluent",160,470),
        entry("cooled_hydrocracker_effluent",160,470),
        entry("liquid_ammonia",17.031,239.82),
        entry("ethylene_oxide",44.05,283.6),entry("ethylene_glycol",62.07,470.4),
        // Half ethylene glycol and half water by mass, as a pseudo-pure liquid.
        entry("antifreeze",24.4,380.15),
        entry("hydrogen_sulfide",34.08,212.8),
        entry("chlorine",70.9,239.1),entry("liquid_chlorine",70.9,239.1),entry("hydrogen_chloride",36.46,188.1),
        entry("vinyl_chloride",62.5,259.8),entry("liquid_vinyl_chloride",62.5,259.8),entry("ethylene_dichloride",98.96,356.7),entry("sulfur_dioxide",64.066,263.13),entry("liquid_sulfur_dioxide",64.066,263.13),
        entry("heavy_oil",400,700),entry("lubricating_oil",450,650),
        entry("createdieselgenerators:crude_oil",250,600),entry("createdieselgenerators:gasoline",100,370),
        entry("createdieselgenerators:diesel",200,520),entry("createdieselgenerators:ethanol",46.07,351.4),
        entry("createdieselgenerators:biodiesel",292,620),entry("createdieselgenerators:plant_oil",880,850));
    private static Map.Entry<String,double[]> entry(String id,double grams,double boiling){return Map.entry(id,new double[]{grams/1000,boiling});}
    private FluidMaterials(){ }
    private static String key(Fluid fluid){var id=BuiltInRegistries.FLUID.getKey(fluid);String path=id.getPath().replaceFirst("^flowing_","");return id.getNamespace().equals(CreatePlant.MOD_ID) ? path : id.getNamespace()+":"+path;}
    public static boolean gas(Fluid fluid){var id=BuiltInRegistries.FLUID.getKey(fluid);return id.getNamespace().equals(CreatePlant.MOD_ID) ? FluidHazards.named(key(fluid)).gas() : fluid.getFluidType().isLighterThanAir();}
    public static double molarMass(FluidStack stack){if(net.createplant.chemistry.WasteGas.contaminated(stack))return net.createplant.chemistry.WasteGas.of(stack).molarMass();return TABLE.getOrDefault(key(stack.getFluid()),new double[]{.028014,373.15})[0];}
    public static double boilingOrder(FluidStack stack){return TABLE.getOrDefault(key(stack.getFluid()),new double[]{.028014,373.15})[1];}
    public static double referenceDensity(Fluid fluid,double kelvin){return FluidHazards.of(new FluidStack(fluid,1)).bar()*1e5*molarMass(new FluidStack(fluid,1))/(R*kelvin)/1000;}
    public static double mass(FluidStack stack){return stack.isEmpty() ? 0 : stack.getAmount()/1000d*FluidThermal.properties(stack).kilogramsPerLitre();}
}
