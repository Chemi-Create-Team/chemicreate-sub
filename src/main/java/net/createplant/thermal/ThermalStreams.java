package net.createplant.thermal;

import net.createplant.registry.*;
import net.createplant.transport.FluidHazards;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** The shared type conversion table for hot and cooled process streams. */
public final class ThermalStreams {
    private ThermalStreams() { }
    private static class Aliases {
        /** Each hot state of a process stream with the fluid it cools to. */
        static final java.util.Map<FluidForm,Fluid> COOLED=java.util.Map.of(
            FluidForm.HOT_SYNTHESIS_MIXTURE,CEProcessFluids.COLD_SYNTHESIS_MIXTURE.get(),
            FluidForm.HOT_NITRIC_OXIDE_MIXTURE,CEProcessFluids.COLD_NITRIC_OXIDE_MIXTURE.get(),
            FluidForm.HOT_OLEFINS,CEProcessFluids.COOLED_OLEFINS.get(),
            FluidForm.HOT_HYDROTREATER_EFFLUENT,CEProcessFluids.COOLED_HYDROTREATER_EFFLUENT.get(),
            FluidForm.HOT_HYDROCRACKER_EFFLUENT,CEProcessFluids.COOLED_HYDROCRACKER_EFFLUENT.get());
        static final java.util.List<SensibleCoolant> LIQUID_COOLANTS=java.util.List.of(
            new SensibleCoolant(Fluids.WATER,FluidForm.WARM_WATER),
            new SensibleCoolant(CEProcessFluids.SLIME_COOLANT.get(),FluidForm.WARM_SLIME_COOLANT));
    }
    private record SensibleCoolant(Fluid cooled,FluidForm warmed) { }
    public static java.util.Map<FluidForm,Fluid> cooledStreams() { return Aliases.COOLED; }
    /** What a stream turns into: {@code output} is 1 mB of the product at its own state, and each batch takes {@code inputAmount} and makes {@code outputAmount}. */
    public record Conversion(int inputAmount,FluidStack output,int outputAmount){
        public Conversion(int inputAmount,Fluid output,int outputAmount){this(inputAmount,new FluidStack(output,1),outputAmount);}
        public Conversion(int inputAmount,FluidForm output,int outputAmount){this(inputAmount,output.stack(1),outputAmount);}
        public FluidStack result(FluidStack source,int batches){var stack=result(batches);if(!stack.isEmpty()){FluidThermal.processComponents(source,stack);}return stack;}
        public FluidStack result(int batches){return batches<=0 ? FluidStack.EMPTY : output.copyWithAmount(Math.multiplyExact(batches,outputAmount));}
    }
    public static Conversion cooling(FluidStack stack){
        if(stack.isEmpty())return null;
        if (stack.is(CEProcessFluids.WATER_GAS.get()) && FluidHazards.of(stack).heat().compareTo(Grade.Heat.AMBIENT) > 0)
            return new Conversion(1, stack.getFluid(), 1);
        for(var pair:Aliases.COOLED.entrySet())if(pair.getKey().matches(stack))return new Conversion(1,pair.getValue(),1);
        if(FluidForm.HOT_REACTOR_COOLANT.matches(stack))return new Conversion(1,FluidForm.WARM_WATER,1);
        if(FluidForm.PRESSURISED_WATER.matches(stack))return new Conversion(1,FluidForm.WARM_WATER,1);
        for(var coolant:Aliases.LIQUID_COOLANTS)if(coolant.warmed().matches(stack))return new Conversion(1,coolant.cooled(),1);
        if(SteamGrades.of(stack)!=null)return new Conversion(1,net.minecraft.world.level.material.Fluids.WATER,1);
        var pair=FluidState.gas(stack) ? PhaseChange.ofVapour(stack) : null;
        return pair==null || cryogenic(stack) ? null : new Conversion(pair.ratio(),pair.liquid(),1);
    }
    public static Conversion coolant(FluidStack stack){
        for(var coolant:Aliases.LIQUID_COOLANTS)if(stack.is(coolant.cooled()))return new Conversion(1,coolant.warmed(),1);
        var pair=PhaseChange.ofLiquid(stack);if(pair!=null)return new Conversion(1,pair.vapour().get(),pair.ratio());
        return null;
    }
    public static boolean cryogenic(FluidStack stack){return stack.is(CEFluids.NITROGEN_GAS.get()) || stack.is(CEFluids.OXYGEN_GAS.get()) || stack.is(CEFusion.HELIUM_GAS.get());}
    public static Conversion liquefaction(FluidStack stack){var pair=cryogenic(stack) && FluidState.gas(stack) ? PhaseChange.ofVapour(stack) : null;return pair==null ? null : new Conversion(pair.ratio(),pair.liquid(),1);}
    public static java.util.List<FluidStack> coolingInputs(){
        var inputs=new java.util.TreeMap<String,FluidStack>();
        for(var hot:cooledStreams().keySet())inputs.put(hot.id(),hot.stack(1));
        for(var coolant:Aliases.LIQUID_COOLANTS)inputs.put(coolant.warmed().id(),coolant.warmed().stack(1));
        for(var form:java.util.List.of(FluidForm.STEAM,FluidForm.HIGH_PRESSURE_STEAM,FluidForm.SUPERHEATED_STEAM,FluidForm.HOT_REACTOR_COOLANT,FluidForm.PRESSURISED_WATER))inputs.put(form.id(),form.stack(1));
        for(var pair:PhaseChange.pairs()) {
            var vapour=new FluidStack(pair.vapour().get(),1);
            if(!cryogenic(vapour))inputs.put(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(vapour.getFluid()).getPath(),vapour);
        }
        var waterGas=new FluidStack(CEProcessFluids.WATER_GAS.get(),1);
        FluidState.setTemperature(waterGas,12);
        inputs.put("water_gas",waterGas);
        return java.util.List.copyOf(inputs.values());
    }
    public static boolean accepts(FluidStack stack,FluidStack required) {
        if(!FluidThermal.mixable(stack,required) || FluidState.gas(stack)!=FluidState.gas(required))return false;
        var type=required.getFluid();
        if(FluidIdentity.controlled(type) && FluidState.pressure(required)>FluidState.NORMAL_PRESSURE
            && FluidState.pressure(stack)<FluidState.pressure(required))return false;
        if(FluidState.temperature(required)>=12 && FluidState.temperature(stack)<FluidState.temperature(required))return false;
        return FluidState.temperature(required)>=12 || FluidIdentity.requirement(type).maxTemperature()>=FluidState.temperature(stack);
    }
}
