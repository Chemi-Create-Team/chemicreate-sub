package net.createplant.transport;

import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.thermal.FluidState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/** Explicit game-scale transport classes, not real engineering pressure ratings.
 * Unknown/foreign fluids are deliberately safe; no fuzzy fluid-name heuristics.
 * Gas fluids use the same finite mB protocol as Create tanks/pumps.
 */
public final class FluidHazards {
    /** How a fluid attacks walls: ordinary acids, hot acid leach liquors (stainless steel gives way; zirconium and resin hold), or fluorides (only nickel alloys hold). */
    public enum Corrosion { NONE, ACID, HOT_ACID, FLUORIDE, HALOGEN, BASE }
    public record Properties(double kelvin, double bar, Corrosion corrosion, boolean gas) {
        public boolean corrosive() { return corrosion != Corrosion.NONE; }
        public net.createplant.thermal.Grade.Heat heat() { return net.createplant.thermal.Grade.Heat.of(kelvin); }
        public net.createplant.thermal.Grade.Pressure pressure() { return net.createplant.thermal.Grade.Pressure.of(bar); }
    }
    public static final Properties ORDINARY = new Properties(293, 1, Corrosion.NONE, false);
    private static final Map<String, Corrosion> STRONG = Map.ofEntries(Map.entry("aluminium_sulfate_solution", Corrosion.HOT_ACID),
        Map.entry("titanyl_sulfate_solution", Corrosion.HOT_ACID), Map.entry("zirconium_sulfate_solution", Corrosion.HOT_ACID),
        Map.entry("hydrogen_fluoride", Corrosion.FLUORIDE), Map.entry("fluorine", Corrosion.FLUORIDE), Map.entry("uranium_hexafluoride", Corrosion.FLUORIDE),
        Map.entry("enriched_uranium_hexafluoride", Corrosion.FLUORIDE), Map.entry("compact_enriched_uranium_hexafluoride", Corrosion.FLUORIDE),
        Map.entry("depleted_uranium_hexafluoride", Corrosion.FLUORIDE),
        // Chlorine and the chlorides that carry it pit stainless steel; caustic solutions etch stoneware.
        Map.entry("chlorine", Corrosion.HALOGEN), Map.entry("hydrogen_chloride", Corrosion.HALOGEN), Map.entry("hydrochloric_acid", Corrosion.HALOGEN),
        Map.entry("sodium_hypochlorite_solution", Corrosion.HALOGEN),
        Map.entry("caustic_soda_solution", Corrosion.BASE), Map.entry("sodium_aluminate_solution", Corrosion.BASE));
    private static final Map<String, Properties> SPECIAL = Map.ofEntries(
        entry("nitric_acid",293,1,true,false), entry("nitrogen_dioxide",293,1,true,true),
        entry("dissolved_spent_fuel",293,1,true,false), entry("plutonium_nitrate_solution",293,1,true,false), entry("high_level_raffinate",293,1,true,false),
        entry("phosphoric_acid",293,1,true,false),
        entry("contaminated_gas",293,1,false,true),
        entry("raw_process_gas",293,1,false,true),
        entry("preheated_crude_oil",620,1,false,false), entry("compressed_air",293,12,false,true), entry("pressurised_hydrogen",293,12,false,true), entry("compressed_hydrogen",293,50,false,true), entry("hydrogen_gas",293,1,false,true), entry("oxygen_gas",293,1,false,true),
        entry("nitrogen_gas",293,1,false,true), entry("ammonia_gas",293,1,false,true),
        entry("argon_gas",293,1,false,true), entry("other_gases",293,1,false,true), entry("air_gas",293,1,false,true), entry("liquid_oxygen",90,1,false,false), entry("liquid_nitrogen",77,1,false,false),
        entry("converter_gas",293,1,false,true), entry("carbon_monoxide",293,1,false,true), entry("carbon_dioxide",293,1,false,true),
        entry("nitric_oxide",293,1,false,true), entry("steam",373,1,false,true), entry("high_pressure_steam",557,70,false,true), entry("superheated_steam",923,70,false,true), entry("hot_reactor_coolant",523,70,false,false), entry("pressurised_water",473,70,false,false),
        entry("ethylene",293,1,false,true), entry("propylene",293,1,false,true),
        entry("synthesis_gas",293,1,false,true), entry("water_gas",293,1,false,true),
        entry("purified_syngas",293,1,false,true), entry("pressurised_carbon_monoxide",293,12,false,true),
        entry("ft_offgas",293,1,false,true), entry("butadiene",293,1,false,true),
        entry("compressed_synthesis_feed",320,12,false,true),
        entry("hot_synthesis_mixture",740,12,false,true),
        entry("cold_synthesis_mixture",310,12,false,true),
        entry("hot_nitric_oxide_mixture",1100,1,false,true),
        entry("cold_nitric_oxide_mixture",310,1,false,true),
        entry("hot_olefins",780,1,false,true),
        entry("molten_polyethylene",480,1,false,false), entry("molten_polypropylene",490,1,false,false),
        entry("resin_process_vapor",293,1,false,true),
        entry("warm_water",330,1,false,false), entry("wastewater",293,1,false,false),
        entry("warm_slime_coolant",330,1,false,false),
        entry("hot_hydrotreater_effluent",650,16,true,false),
        entry("cooled_hydrotreater_effluent",310,16,true,false), entry("hydrogen_sulfide",293,1,true,true),
        entry("liquid_ammonia",240,1,false,false), entry("ethylene_oxide",293,1,false,true), entry("hot_hydrocracker_effluent",690,16,true,false),
        entry("cooled_hydrocracker_effluent",310,16,true,false),
        entry("sulfur_dioxide",293,1,true,true), entry("sulfur_trioxide",293,1,true,true), entry("sulfuric_acid",293,1,true,false), entry("sulfurous_acid",293,1,true,false),
        entry("hydrogen_fluoride",293,1,true,true), entry("fluorine",293,1,true,true),
        entry("aluminium_sulfate_solution",353,1,true,false), entry("titanyl_sulfate_solution",353,1,true,false), entry("zirconium_sulfate_solution",353,1,true,false),
        entry("iron_sulfate_solution",333,1,true,false), entry("copper_sulfate_solution",333,1,true,false), entry("zinc_sulfate_solution",333,1,true,false), entry("tin_sulfate_solution",333,1,true,false),
        entry("copper_leach_slurry",293,1,true,false), entry("iron_leach_slurry",293,1,true,false),
        entry("zinc_leach_slurry",293,1,true,false), entry("tin_leach_slurry",293,1,true,false),
        entry("dilute_sulfuric_acid",293,1,true,false),
        entry("uranyl_nitrate_solution",293,1,true,false),
        entry("uranium_hexafluoride",340,1,true,true), entry("enriched_uranium_hexafluoride",340,1,true,true),
        entry("compact_enriched_uranium_hexafluoride",340,1,true,true),
        entry("depleted_uranium_hexafluoride",340,1,true,true),
        // Chlor-alkali: chloride brine and caustic soda attack carbon steel; the Bayer liquor leaves the digester at 150 C.
        entry("brine",293,1,true,false), entry("chlorine",293,1,true,true), entry("liquid_chlorine",239,1,false,false), entry("liquid_sulfur_dioxide",263,1,false,false), entry("liquid_vinyl_chloride",260,1,false,false), entry("liquid_propylene",225,1,false,false), entry("caustic_soda_solution",293,1,true,false),
        entry("sodium_aluminate_solution",423,1,true,false), entry("hydrogen_chloride",293,1,true,true), entry("hydrochloric_acid",293,1,true,false), entry("sodium_hypochlorite_solution",293,1,true,false),
        entry("ethylene_dichloride",293,1,false,false), entry("vinyl_chloride",293,1,false,true),
        entry("deuterium_gas",293,1,false,true), entry("tritium_gas",293,1,false,true),
        entry("helium_gas",293,1,false,true), entry("helium_recovery_feed",293,1,false,true), entry("liquid_helium",4,1,false,false));
    private static Map.Entry<String, Properties> entry(String id,int k,int bar,boolean acid,boolean gas) {
        return Map.entry(id,new Properties(k,bar,acid ? STRONG.getOrDefault(id,Corrosion.ACID) : Corrosion.NONE,gas));
    }
    public static Properties nominal(FluidStack fluid) {
        if (fluid.isEmpty()) return ORDINARY;
        if (net.createplant.mixture.MixtureHelper.isMixture(fluid)) {
            long heat=0,pressure=0,amount=0;
            Corrosion corrosion=Corrosion.NONE;
            if(fluid.has(net.createplant.registry.CEMixtures.MIXTURE_CONTENTS.get()))for(var part:net.createplant.mixture.MixtureHelper.components(fluid)) {
                heat+=(long)FluidState.temperature(part)*part.getAmount();
                pressure+=(long)FluidState.pressure(part)*part.getAmount();
                amount+=part.getAmount();
                var property=nominal(part.getFluid()).corrosion();
                if(property.ordinal()>corrosion.ordinal())corrosion=property;
            }
            int temperature=amount==0 ? FluidState.AMBIENT : (int)((heat+amount/2)/amount);
            int pressureLevel=amount==0 ? FluidState.NORMAL_PRESSURE : (int)((pressure+amount/2)/amount);
            return new Properties(FluidState.kelvin(temperature),FluidState.bar(pressureLevel),corrosion,net.createplant.mixture.MixtureHelper.gas(fluid));
        }
        return nominal(fluid.getFluid());
    }
    public static Properties nominal(net.minecraft.world.level.material.Fluid fluid) {
        var id=BuiltInRegistries.FLUID.getKey(fluid);
        String path=id.getPath();
        if (path.startsWith("flowing_")) path=path.substring(8);
        return id.getNamespace().equals(CreatePlant.MOD_ID) ? named(path) : EXTERNAL.getOrDefault(id.getNamespace()+":"+path,ORDINARY);
    }
    public static Properties of(FluidStack fluid) {
        if(fluid.isEmpty())return ORDINARY;
        var properties=nominal(fluid);
        boolean mixture=net.createplant.mixture.MixtureHelper.isMixture(fluid);
        // A fluid without state data keeps its own temperature and pressure instead of the nearest level's.
        double kelvin=mixture || fluid.has(net.createplant.registry.CEThermal.TEMPERATURE.get()) || fluid.has(net.createplant.registry.CEThermal.HEAT_GRADE.get())
            ? FluidState.kelvin(FluidState.temperature(fluid)) : properties.kelvin();
        double bar=mixture || fluid.has(net.createplant.registry.CEThermal.PRESSURE.get()) ? FluidState.bar(FluidState.pressure(fluid)) : properties.bar();
        return new Properties(kelvin,bar,properties.corrosion(),FluidState.gas(fluid));
    }
    public static Properties named(String path) { return SPECIAL.getOrDefault(path,ORDINARY); }
    private static final Map<String, Properties> EXTERNAL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Transport class of another mod's fluid, by the id of its source fluid. */
    public static void register(net.minecraft.resources.ResourceLocation fluid, Properties properties) { EXTERNAL.put(fluid.toString(), properties); }
    private FluidHazards() { }
}
