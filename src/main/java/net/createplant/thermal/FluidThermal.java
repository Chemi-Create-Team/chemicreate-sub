package net.createplant.thermal;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidThermal {
    public record Properties(double initialKelvin,double kilogramsPerLitre,double joulesPerKilogramKelvin) {
        public Properties {
            if(!Double.isFinite(initialKelvin) || initialKelvin<=0)throw new IllegalArgumentException("Invalid nominal temperature");
            if (!Double.isFinite(kilogramsPerLitre) || kilogramsPerLitre<=0 || kilogramsPerLitre>100
                || !Double.isFinite(joulesPerKilogramKelvin) || joulesPerKilogramKelvin<=0 || joulesPerKilogramKelvin>100000)
                throw new IllegalArgumentException("Invalid thermal material properties");
        }
    }
    private static final Map<Fluid,Properties> PROPERTIES=new IdentityHashMap<>();
    private static final Map<String,double[]> MATERIALS=Map.ofEntries(
        Map.entry("ethanol",new double[]{.789,2440}),Map.entry("biodiesel",new double[]{.88,2000}),Map.entry("plant_oil",new double[]{.92,1970}),
        Map.entry("warm_water",new double[]{1,4180}),Map.entry("crude_oil",new double[]{.85,2000}),
        Map.entry("heavy_oil",new double[]{.95,1900}),
        Map.entry("lubricating_oil",new double[]{.88,2000}),Map.entry("diesel",new double[]{.83,2100}),
        Map.entry("liquid_ammonia",new double[]{.68182,4700}),Map.entry("ethylene_glycol",new double[]{1.113,2400}),Map.entry("antifreeze",new double[]{1.07,3300}),
        Map.entry("methanol",new double[]{.8,2500}),Map.entry("ammonium_chloride_liquor",new double[]{1.05,4000}),Map.entry("low_sulfur_diesel",new double[]{.83,2100}),
        Map.entry("hot_hydrotreater_effluent",new double[]{.83,2100}),
        Map.entry("hot_hydrocracker_effluent",new double[]{.8,2150}),Map.entry("cooled_hydrocracker_effluent",new double[]{.8,2150}),Map.entry("cooled_hydrotreater_effluent",new double[]{.83,2100}),
        Map.entry("gasoline",new double[]{.74,2200}),Map.entry("naphtha",new double[]{.75,2200}),
        Map.entry("hydrogen_gas",new double[]{.000084,14300}),Map.entry("oxygen_gas",new double[]{.00143,918}),
        Map.entry("nitrogen_gas",new double[]{.00125,1040}),Map.entry("liquid_nitrogen",new double[]{.8075,2040}),Map.entry("wastewater",new double[]{1,4180}),Map.entry("heavy_water",new double[]{1.1056,4210}),Map.entry("deuterium_depleted_water",new double[]{1,4180}),Map.entry("helium_recovery_feed",new double[]{.000437,2000}),Map.entry("ammonia_gas",new double[]{.00073,2060}),
        Map.entry("converter_gas",new double[]{.0011644,1040}),
        Map.entry("carbon_monoxide",new double[]{.0011644,1040}),Map.entry("carbon_dioxide",new double[]{.00182935,844}),
        Map.entry("deuterium_gas",new double[]{.000168,7200}),Map.entry("tritium_gas",new double[]{.000251,4800}),
        Map.entry("helium_gas",new double[]{.000166,5190}),Map.entry("liquid_helium",new double[]{.125,5200}),
        Map.entry("sulfuric_acid",new double[]{1.84,1380}),Map.entry("aluminium_sulfate_solution",new double[]{1.3,3300}),Map.entry("brine",new double[]{1.2,3300}),Map.entry("sodium_hypochlorite_solution",new double[]{1.1,3600}),Map.entry("chlorine",new double[]{.0032,480}),Map.entry("liquid_chlorine",new double[]{1.5616,960}),Map.entry("caustic_soda_solution",new double[]{1.33,3300}),Map.entry("sodium_aluminate_solution",new double[]{1.35,3200}),Map.entry("hydrogen_chloride",new double[]{.00164,800}),Map.entry("hydrochloric_acid",new double[]{1.18,2500}),Map.entry("ethylene_dichloride",new double[]{1.25,1300}),Map.entry("vinyl_chloride",new double[]{.0028,860}),Map.entry("liquid_vinyl_chloride",new double[]{.91,1150}),Map.entry("liquid_sulfur_dioxide",new double[]{1.4588025337,1360}),Map.entry("liquid_propylene",new double[]{.6140899580,2200}),Map.entry("titanyl_sulfate_solution",new double[]{1.4,3000}),Map.entry("zirconium_sulfate_solution",new double[]{1.35,3100}),Map.entry("nitric_acid",new double[]{1.51,1710}));
    private FluidThermal() { }
    public static synchronized Properties properties(FluidStack stack) {
        if (net.createplant.chemistry.WasteGas.contaminated(stack)) {var gas=net.createplant.chemistry.WasteGas.of(stack);return new Properties(293.15,gas.density(),gas.heatCapacity());}
        return PROPERTIES.computeIfAbsent(stack.getFluid(),fluid->{
            var id=BuiltInRegistries.FLUID.getKey(fluid);String name=id.getPath();
            double initial=Math.clamp(fluid.getFluidType().getTemperature(),1,10000);
            if (id.getNamespace().equals(net.createplant.CreatePlant.MOD_ID) && initial==293) initial=293.15;
            if (id.getNamespace().equals("minecraft") && (name.equals("water") || name.equals("flowing_water"))) return new Properties(293.15,1,4180);
            if (fluid==net.createplant.registry.CEFluids.STEAM.get()) return new Properties(373.15,1,4180);
            double[] material=(id.getNamespace().equals(net.createplant.CreatePlant.MOD_ID) || id.getNamespace().equals("createdieselgenerators")) ? MATERIALS.get(name.startsWith("flowing_") ? name.substring(8) : name) : null;
            if (material!=null) return new Properties(initial,material[0],material[1]);
            if (FluidMaterials.gas(fluid)) return new Properties(initial,FluidMaterials.referenceDensity(fluid,initial),1000);
            return new Properties(initial,Math.clamp(Math.abs(fluid.getFluidType().getDensity())/1000d,.000001,100),1000);
        });
    }
    public static synchronized void register(Fluid fluid,Properties properties) {
        if (PROPERTIES.containsKey(fluid)) throw new IllegalStateException("Register thermal properties before first use");
        PROPERTIES.put(fluid,java.util.Objects.requireNonNull(properties));
    }
    public static boolean tracked(FluidStack stack) { return false; }
    public static boolean valid(FluidStack stack) { return true; }
    public static double kelvin(FluidStack stack) { return net.createplant.transport.FluidHazards.of(stack).kelvin(); }
    public static double heatCapacity(FluidStack stack) {
        if (stack.isEmpty()) return 0;
        var p=properties(stack);return stack.getAmount()/1000d*p.kilogramsPerLitre()*p.joulesPerKilogramKelvin();
    }
    public static double energy(FluidStack stack){return heatCapacity(stack)*kelvin(stack); }
    public static FluidStack at(FluidStack source,double ignoredKelvin) { return source; }
    public static void processComponents(FluidStack source,FluidStack product) {
        product.applyComponents(source.getComponentsPatch());
        product.remove(net.createplant.registry.CEThermal.HEAT_GRADE.get());
        product.remove(net.createplant.registry.CEThermal.EXPANDED_STEAM.get());
        product.remove(net.createplant.registry.CEThermal.CONDENSATE.get());
    }
    public static boolean compatible(FluidStack a,FluidStack b) { return FluidStack.isSameFluidSameComponents(a,b); }
    public static boolean mixable(FluidStack a,FluidStack b) {
        if(compatible(a,b))return true;
        if(a.isEmpty() || b.isEmpty() || a.getFluid()!=b.getFluid())return false;
        var first=a.copy();var second=b.copy();
        first.remove(net.createplant.registry.CEThermal.HEAT_GRADE.get());second.remove(net.createplant.registry.CEThermal.HEAT_GRADE.get());
        first.remove(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get());second.remove(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get());
        first.remove(net.createplant.registry.CEThermal.CONDENSATE.get());second.remove(net.createplant.registry.CEThermal.CONDENSATE.get());
        return compatible(first,second);
    }
    public static FluidStack mix(FluidStack stored,FluidStack incoming,int accepted) {
        if (stored.isEmpty() || incoming.isEmpty() || accepted<=0 || accepted>incoming.getAmount() || !mixable(stored,incoming)) throw new IllegalArgumentException("Incompatible fluid mixture");
        var result=stored.copyWithAmount(Math.addExact(stored.getAmount(),accepted));
        var heat=net.createplant.registry.CEThermal.HEAT_GRADE.get();
        if(!stored.has(heat) || !incoming.has(heat))result.remove(heat);
        else result.set(heat,Math.min(stored.get(heat),incoming.get(heat)));
        var condensate=net.createplant.registry.CEThermal.CONDENSATE.get();
        if (!stored.has(condensate) || !incoming.has(condensate)) result.remove(condensate);
        var impurity=net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get();
        int suspended=(int)(((long)stored.getOrDefault(impurity,0)*stored.getAmount()+(long)incoming.getOrDefault(impurity,0)*accepted+result.getAmount()/2)/result.getAmount());
        if(suspended>0)result.set(impurity,suspended);else result.remove(impurity);
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null && server.isSameThread())net.createplant.nuclear.RadiationSources.mix(result,stored,incoming,accepted,server.overworld().getGameTime());
        return result;
    }
    /** Adds {@code amount} of a compatible stack to {@code stored}, preserving the stored fluid type and components. */
    public static FluidStack add(FluidStack stored,FluidStack incoming,int amount) {
        if (stored.isEmpty()) return incoming.copyWithAmount(amount);
        return stored.has(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get()) || incoming.has(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get()) || stored.has(net.createplant.registry.CEThermal.HEAT_GRADE.get()) || incoming.has(net.createplant.registry.CEThermal.HEAT_GRADE.get()) || tracked(stored) || tracked(incoming) || net.createplant.nuclear.RadiationSources.profile(stored) != null ? mix(stored,incoming,amount) : stored.copyWithAmount(stored.getAmount()+amount);
    }
    public static Component readout(FluidStack stack) {
        var result=Grade.describe(Grade.Heat.of(kelvin(stack)),net.createplant.transport.FluidHazards.of(stack).pressure());
        int impurity=stack.getOrDefault(net.createplant.registry.CEThermal.SUSPENDED_IMPURITY.get(),0);
        return impurity>0 ? result.copy().append(" · ").append(Component.translatable("wet_process.create_refinery_n_reactors.impurity",impurity/10d)) : result;
    }
}
