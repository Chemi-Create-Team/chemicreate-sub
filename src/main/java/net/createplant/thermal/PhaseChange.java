package net.createplant.thermal;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.registry.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed registered liquid/gas conversions shared by cooling, liquefaction and feed meters. */
public final class PhaseChange {
    public record Pair(Supplier<? extends Fluid> vapour,FluidForm liquid){
        public int ratio(){return (int)Math.round(FluidThermal.nominalProperties(liquid).kilogramsPerLitre()/FluidThermal.nominalProperties(vapour.get()).kilogramsPerLitre());}
    }
    private static final List<Pair> PAIRS = List.of(new Pair(CEFluids.AMMONIA_GAS, FluidForm.LIQUID_AMMONIA),
        new Pair(CEFluids.OXYGEN_GAS, FluidForm.LIQUID_OXYGEN), new Pair(CEFluids.NITROGEN_GAS, FluidForm.LIQUID_NITROGEN), new Pair(CEChlorAlkali.CHLORINE, FluidForm.LIQUID_CHLORINE),
        new Pair(CEUranium.SULFUR_DIOXIDE, FluidForm.LIQUID_SULFUR_DIOXIDE), new Pair(CEChlorAlkali.VINYL_CHLORIDE, FluidForm.LIQUID_VINYL_CHLORIDE),
        new Pair(CEPetroFluids.PROPYLENE, FluidForm.LIQUID_PROPYLENE),
        new Pair(CEFusion.HELIUM_GAS, FluidForm.LIQUID_HELIUM));
    private PhaseChange(){ }
    public static List<Pair> pairs(){return PAIRS;}
    public static Pair ofLiquid(FluidStack stack){return PAIRS.stream().filter(p->p.liquid().matches(stack)).findFirst().orElse(null);}
    public static Pair ofVapour(FluidStack stack){return PAIRS.stream().filter(p->stack.is(p.vapour().get())).findFirst().orElse(null);}

    public static int ratio(FluidStack stack) {
        for (var pair : PAIRS) if (stack.getFluid() == pair.vapour().get()) return pair.ratio();
        if (stack.is(net.minecraft.world.level.material.Fluids.WATER)) return 1;
        var type = stack.getFluid();
        var nominal = FluidThermal.nominalProperties(type);
        double referenceGas = FluidMaterials.molarMass(type) * 100 / (FluidMaterials.R * 298.15);
        double liquidDensity = FluidMaterials.gas(type) ? 1 : nominal.kilogramsPerLitre();
        return (int)Math.clamp(Math.round(liquidDensity / referenceGas),1,1_000_000);
    }

    /** A whole-number conversion quantum. Unconverted input stays in its original tank. */
    public record Transfer(FluidStack prototype, int consumed, int produced) {
        public FluidStack output(int batches) {
            int count = (int)Math.min(batches,Integer.MAX_VALUE/(long)produced);
            return count<=0 ? FluidStack.EMPTY : (net.createplant.mixture.MixtureHelper.isMixture(prototype)
                ? net.createplant.mixture.MixtureHelper.portion(prototype,count*produced) : prototype.copyWithAmount(count*produced));
        }
        public int batches(int input) { return Math.min(input/consumed,Integer.MAX_VALUE/produced); }
        public int fitting(int batches,net.neoforged.neoforge.fluids.capability.IFluidHandler target) {
            return fitting(batches,stack->target.fill(stack,net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE));
        }
        int fitting(int batches,java.util.function.ToIntFunction<FluidStack> accepted) {
            int low=0,high=batches;
            while(low<high) {
                int middle=(int)((low+(long)high+1)/2);
                FluidStack result=output(middle);
                if(accepted.applyAsInt(result)==result.getAmount())low=middle;
                else high=middle-1;
            }
            return low;
        }
    }

    public static Transfer transfer(FluidStack fluid,int temperature,int pressure) {
        FluidStack result=FluidState.with(fluid,temperature,pressure);
        boolean before=FluidState.gas(fluid), after=FluidState.gas(result);
        int ratio=before==after ? 1 : ratio(fluid);
        if(before && !after && result.is(net.minecraft.world.level.material.Fluids.WATER)) {
            result.set(net.createplant.registry.CEThermal.CONDENSATE.get(),true);
            result.remove(net.createplant.registry.CEThermal.EXPANDED_STEAM.get());
        }
        return new Transfer(result,before ? ratio : 1,after ? ratio : 1);
    }
}
