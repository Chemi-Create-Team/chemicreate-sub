package net.createplant.thermal;

import java.util.List;
import java.util.function.Supplier;
import net.createplant.registry.*;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fixed registered liquid/gas conversions shared by cooling, liquefaction and feed meters. */
public final class PhaseChange {
    public record Pair(Supplier<? extends Fluid> vapour,Supplier<? extends Fluid> liquid){
        public int ratio(){return (int)Math.round(FluidThermal.properties(new FluidStack(liquid.get(),1)).kilogramsPerLitre()/FluidThermal.properties(new FluidStack(vapour.get(),1)).kilogramsPerLitre());}
    }
    private static final List<Pair> PAIRS = List.of(new Pair(CEFluids.AMMONIA_GAS, CEProcessFluids.LIQUID_AMMONIA),
        new Pair(CEFluids.OXYGEN_GAS, CEFluids.LIQUID_OXYGEN), new Pair(CEFluids.NITROGEN_GAS, CEProcessFluids.LIQUID_NITROGEN), new Pair(CEChlorAlkali.CHLORINE, CEChlorAlkali.LIQUID_CHLORINE),
        new Pair(CEUranium.SULFUR_DIOXIDE, CEProcessFluids.LIQUID_SULFUR_DIOXIDE), new Pair(CEChlorAlkali.VINYL_CHLORIDE, CEProcessFluids.LIQUID_VINYL_CHLORIDE),
        new Pair(CEPetroFluids.PROPYLENE, CEProcessFluids.LIQUID_PROPYLENE),
        new Pair(CEFusion.HELIUM_GAS, CEFusion.LIQUID_HELIUM));
    private PhaseChange(){ }
    public static List<Pair> pairs(){return PAIRS;}
    public static Pair ofLiquid(FluidStack stack){return PAIRS.stream().filter(p->stack.is(p.liquid().get())).findFirst().orElse(null);}
    public static Pair ofVapour(FluidStack stack){return PAIRS.stream().filter(p->stack.is(p.vapour().get())).findFirst().orElse(null);}
}
