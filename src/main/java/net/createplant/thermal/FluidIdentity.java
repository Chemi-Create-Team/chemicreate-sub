package net.createplant.thermal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createplant.CreatePlant;
import net.createplant.registry.CEThermal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Old entries remain registered so saved worlds and data packs can read their original state. */
public final class FluidIdentity {
    private enum Condition { LIQUID, STEAM, HOT, PRESSURE, HOT_LIQUID, PRESSURED_LIQUID }
    private record Alias(ResourceLocation substance,int temperature,int pressure,Condition condition) { }
    private static final Map<ResourceLocation,Alias> ALIASES=new HashMap<>();
    private static final Map<ResourceLocation,List<ResourceLocation>> VARIANTS=new HashMap<>();

    static {
        water("warm_water",9,4,Condition.LIQUID);
        water("steam",12,4,Condition.STEAM);
        water("high_pressure_steam",12,12,Condition.STEAM);
        water("superheated_steam",16,12,Condition.STEAM);
        water("pressurised_water",10,8,Condition.PRESSURED_LIQUID);
        water("hot_reactor_coolant",11,12,Condition.HOT_LIQUID);
        phase("liquid_nitrogen","nitrogen_gas",1);
        phase("liquid_oxygen","oxygen_gas",1);
        phase("liquid_helium","helium_gas",0);
        phase("liquid_ammonia","ammonia_gas",4);
        phase("liquid_chlorine","chlorine",4);
        phase("liquid_sulfur_dioxide","sulfur_dioxide",6);
        phase("liquid_vinyl_chloride","vinyl_chloride",5);
        phase("liquid_propylene","propylene",4);
        alias("compressed_air",CreatePlant.location("air_gas"),8,8,Condition.PRESSURE);
        alias("pressurised_hydrogen",CreatePlant.location("hydrogen_gas"),8,8,Condition.PRESSURE);
        alias("compressed_hydrogen",CreatePlant.location("hydrogen_gas"),8,12,Condition.PRESSURE);
        alias("pressurised_carbon_monoxide",CreatePlant.location("carbon_monoxide"),8,8,Condition.PRESSURE);
        alias("compressed_synthesis_feed",CreatePlant.location("synthesis_gas"),8,8,Condition.PRESSURE);
        hot("hot_synthesis_mixture","cold_synthesis_mixture",12,8);
        hot("hot_nitric_oxide_mixture","cold_nitric_oxide_mixture",12,4);
        hot("hot_olefins","cooled_olefins",12,4);
        hot("hot_hydrotreater_effluent","cooled_hydrotreater_effluent",12,8);
        hot("hot_hydrocracker_effluent","cooled_hydrocracker_effluent",12,8);
        hot("warm_slime_coolant","slime_coolant",9,4);
        alias("preheated_crude_oil",ResourceLocation.fromNamespaceAndPath("createdieselgenerators","crude_oil"),12,4,Condition.HOT);
    }

    private FluidIdentity() { }

    private static void water(String name,int temp,int pressure,Condition condition) {
        alias(name,ResourceLocation.withDefaultNamespace("water"),temp,pressure,condition);
    }
    private static void phase(String name,String substance,int temp) { alias(name,CreatePlant.location(substance),temp,4,Condition.LIQUID); }
    private static void hot(String name,String substance,int temp,int pressure) { alias(name,CreatePlant.location(substance),temp,pressure,Condition.HOT); }
    private static void alias(String name,ResourceLocation substance,int temp,int pressure,Condition condition) {
        var key=CreatePlant.location(name);
        ALIASES.put(key,new Alias(substance,temp,pressure,condition));
        ALIASES.put(CreatePlant.location("flowing_"+name),ALIASES.get(key));
        VARIANTS.computeIfAbsent(substance,ignored -> new ArrayList<>()).add(key);
    }
    /** Every fluid stack constructed passes here, so the lookup reads only the tables fixed when this class loads. */
    private static Alias lookup(Fluid fluid) {
        var id=BuiltInRegistries.FLUID.getKey(fluid);
        var alias=ALIASES.get(id);
        return alias==null || BuiltInRegistries.FLUID.get(id)!=fluid ? null : alias;
    }
    public static boolean legacy(Fluid fluid) { return lookup(fluid)!=null; }
    public static boolean controlled(Fluid fluid) { return VARIANTS.containsKey(BuiltInRegistries.FLUID.getKey(fluid)); }

    public static Fluid canonical(Fluid fluid) {
        var old=lookup(fluid);
        return old==null || !BuiltInRegistries.FLUID.containsKey(old.substance()) ? fluid : BuiltInRegistries.FLUID.get(old.substance());
    }

    /** Called once after decoding an old stack or constructing an output from an old recipe entry. */
    public static Fluid migrate(FluidStack stack,Fluid registered) {
        if(stack.isEmpty() || !CEThermal.HEAT_GRADE.isBound())return registered;
        FluidState.migrateGrade(stack);
        var old=lookup(registered);
        if(old==null || !BuiltInRegistries.FLUID.containsKey(old.substance()))return registered;
        var substance=BuiltInRegistries.FLUID.get(old.substance());
        if(!stack.has(CEThermal.TEMPERATURE.get()))FluidState.setTemperature(stack,substance,old.temperature());
        if(!stack.has(CEThermal.PRESSURE.get()))FluidState.setPressure(stack,substance,old.pressure());
        return substance;
    }

    public static FluidRequirement requirement(Fluid type) {
        var old=lookup(type);
        if(old!=null)return switch(old.condition()) {
            case LIQUID -> new FluidRequirement(old.temperature()<8 ? 0 : old.temperature(),16,0,16,FluidRequirement.Phase.LIQUID);
            case STEAM -> new FluidRequirement(old.temperature()==16 ? 16 : 0,16,old.pressure()==4 ? 0 : old.pressure(),old.pressure()==4 ? 7 : 16,FluidRequirement.Phase.GAS);
            case HOT -> new FluidRequirement(old.temperature(),16,0,16,FluidRequirement.Phase.ANY);
            case PRESSURE -> new FluidRequirement(0,16,old.pressure(),16,FluidRequirement.Phase.GAS);
            case HOT_LIQUID, PRESSURED_LIQUID -> new FluidRequirement(old.temperature(),16,old.pressure(),16,FluidRequirement.Phase.LIQUID);
        };
        var id=BuiltInRegistries.FLUID.getKey(type);
        if(id.getPath().startsWith("cold_") || id.getPath().startsWith("cooled_"))
            return new FluidRequirement(0,11,0,16,FluidRequirement.Phase.ANY);
        if(controlled(type) || FluidMaterials.known(type))return new FluidRequirement(0,16,0,16,
            FluidState.gas(new FluidStack(type,1)) ? FluidRequirement.Phase.GAS : FluidRequirement.Phase.LIQUID);
        return FluidRequirement.ANY;
    }

    public static boolean matches(FluidStack stack,Fluid requested) {
        if(stack.isEmpty())return false;
        var old=lookup(requested);
        if(old==null || !BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(old.substance()))return false;
        return requirement(requested).matches(stack);
    }

    public static boolean matchesVariant(FluidStack stack,java.util.function.Predicate<Fluid> candidates) {
        if(stack.isEmpty())return false;
        var list=VARIANTS.get(BuiltInRegistries.FLUID.getKey(stack.getFluid()));
        if(list==null)return false;
        for(var id:list) {
            var variant=BuiltInRegistries.FLUID.get(id);
            if(candidates.test(variant) && matches(stack,variant))return true;
        }
        return false;
    }

    /** The retired entries that name states of a substance, such as steam and warm water for water. */
    public static List<Fluid> variants(Fluid substance) {
        var list=VARIANTS.get(BuiltInRegistries.FLUID.getKey(substance));
        return list==null ? List.of() : list.stream().map(BuiltInRegistries.FLUID::get).toList();
    }

    public static Fluid liquidVariant(Fluid substance) {
        var list=VARIANTS.get(BuiltInRegistries.FLUID.getKey(substance));
        if(list==null)return substance;
        for(var key:list)if(key.getPath().startsWith("liquid_"))return BuiltInRegistries.FLUID.get(key);
        return substance;
    }
}
