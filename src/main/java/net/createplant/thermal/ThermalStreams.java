package net.createplant.thermal;

import net.createplant.registry.*;
import net.createplant.transport.FluidHazards;
import net.neoforged.neoforge.fluids.FluidStack;

/** The shared type conversion table for hot and cooled process streams. */
public final class ThermalStreams {
    private ThermalStreams() { }
    private static class Aliases {
        static final java.util.Map<net.minecraft.world.level.material.Fluid,net.minecraft.world.level.material.Fluid> COOLED=java.util.Map.of(
            CEProcessFluids.HOT_SYNTHESIS_MIXTURE.get(),CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),
            CEProcessFluids.HOT_NITRIC_OXIDE_MIXTURE.get(),CEProcessFluids.COLD_NITRIC_OXIDE_MIXTURE.get(),
            CEProcessFluids.HOT_OLEFINS.get(),CEProcessFluids.COOLED_OLEFINS.get(),
            CEProcessFluids.HOT_HYDROTREATER_EFFLUENT.get(),CEProcessFluids.COOLED_HYDROTREATER_EFFLUENT.get(),
            CEProcessFluids.HOT_HYDROCRACKER_EFFLUENT.get(),CEProcessFluids.COOLED_HYDROCRACKER_EFFLUENT.get());
    }
    public static java.util.Map<net.minecraft.world.level.material.Fluid,net.minecraft.world.level.material.Fluid> cooledStreams() { return Aliases.COOLED; }
    public record Conversion(int inputAmount,net.minecraft.world.level.material.Fluid output,int outputAmount){
        public FluidStack result(FluidStack source,int batches){var stack=result(batches);if(!stack.isEmpty()){FluidThermal.processComponents(source,stack);}return stack;}
        public FluidStack result(int batches){return batches<=0 ? FluidStack.EMPTY : new FluidStack(output,Math.multiplyExact(batches,outputAmount));}
    }
    public static Conversion cooling(FluidStack stack){
        if(stack.isEmpty())return null;
        if (stack.is(CEProcessFluids.WATER_GAS.get()) && FluidHazards.of(stack).heat().compareTo(Grade.Heat.AMBIENT) > 0)
            return new Conversion(1, stack.getFluid(), 1);
        var cooled=Aliases.COOLED.get(stack.getFluid());if(cooled!=null)return new Conversion(1,cooled,1);
        if(stack.is(CEProcessFluids.HOT_REACTOR_COOLANT.get()))return new Conversion(1,CEProcessFluids.WARM_WATER.get(),1);
        if(stack.is(CEProcessFluids.PRESSURISED_WATER.get()))return new Conversion(1,CEProcessFluids.WARM_WATER.get(),1);
        if(stack.is(CEProcessFluids.WARM_WATER.get()))return new Conversion(1,net.minecraft.world.level.material.Fluids.WATER,1);
        if(SteamGrades.of(stack)!=null)return new Conversion(1,net.minecraft.world.level.material.Fluids.WATER,1);
        var pair=PhaseChange.ofVapour(stack);
        return pair==null || cryogenic(stack) ? null : new Conversion(pair.ratio(),pair.liquid().get(),1);
    }
    public static Conversion coolant(FluidStack stack){
        if(stack.is(net.minecraft.world.level.material.Fluids.WATER))return new Conversion(1,CEProcessFluids.WARM_WATER.get(),1);
        var pair=PhaseChange.ofLiquid(stack);if(pair!=null)return new Conversion(1,pair.vapour().get(),pair.ratio());
        return null;
    }
    public static boolean cryogenic(FluidStack stack){return stack.is(CEFluids.NITROGEN_GAS.get()) || stack.is(CEFluids.OXYGEN_GAS.get()) || stack.is(CEFusion.HELIUM_GAS.get());}
    public static Conversion liquefaction(FluidStack stack){var pair=cryogenic(stack) ? PhaseChange.ofVapour(stack) : null;return pair==null ? null : new Conversion(pair.ratio(),pair.liquid().get(),1);}
    public static java.util.List<FluidStack> coolingInputs(){
        var fluids=new java.util.ArrayList<>(cooledStreams().keySet());fluids.add(CEProcessFluids.WARM_WATER.get());fluids.add(CEFluids.STEAM.get());fluids.add(CEProcessFluids.HIGH_PRESSURE_STEAM.get());fluids.add(CEProcessFluids.SUPERHEATED_STEAM.get());fluids.add(CEProcessFluids.HOT_REACTOR_COOLANT.get());fluids.add(CEProcessFluids.PRESSURISED_WATER.get());
        for(var pair:PhaseChange.pairs())if(!cryogenic(new FluidStack(pair.vapour().get(),1)))fluids.add(pair.vapour().get());
        fluids.add(CEProcessFluids.WATER_GAS.get());
        fluids.sort(java.util.Comparator.comparing(f->net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).toString()));
        return fluids.stream().map(fluid -> {
            var stack = new FluidStack(fluid, 1);
            if (stack.is(CEProcessFluids.WATER_GAS.get())) stack.set(CEThermal.HEAT_GRADE.get(), Grade.Heat.HOT.ordinal());
            return stack;
        }).toList();
    }
    public static java.util.List<net.minecraft.world.level.material.Fluid> coolants(){var fluids=new java.util.ArrayList<net.minecraft.world.level.material.Fluid>();fluids.add(net.minecraft.world.level.material.Fluids.WATER);for(var pair:PhaseChange.pairs())fluids.add(pair.liquid().get());return java.util.List.copyOf(fluids);}
    public static boolean accepts(FluidStack stack,FluidStack required) { return FluidThermal.mixable(stack,required); }
}
