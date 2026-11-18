package net.createplant.thermal;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/** One pressure-grade step, shared by compressors and reducing valves. Gas quantities are stored in atmospheric-equivalent mB. */
public final class Compression {
    private record Pair(Supplier<? extends Fluid> low, Supplier<? extends Fluid> high) { }
    private static final List<Pair> PAIRS=List.of(
        new Pair(CEFluids.AIR_GAS,CEProcessFluids.COMPRESSED_AIR),
        new Pair(CEProcessFluids.SYNTHESIS_GAS,CEProcessFluids.COMPRESSED_SYNTHESIS_FEED),
        new Pair(CEFluids.HYDROGEN_GAS,CEProcessFluids.PRESSURISED_HYDROGEN),
        new Pair(CEProcessFluids.PRESSURISED_HYDROGEN,CEProcessFluids.COMPRESSED_HYDROGEN));
    private Compression() { }
    /** Each low-pressure fluid with the fluid one compression step makes of it, in compressor order. */
    public static java.util.Map<Fluid,Fluid> steps() {
        var steps=new java.util.LinkedHashMap<Fluid,Fluid>();
        for(var pair:PAIRS) steps.put(pair.low().get(),pair.high().get());
        return steps;
    }
    public static boolean isAir(FluidStack stack) {
        return stack.is(CEFluids.AIR_GAS.get()) || stack.is(CEProcessFluids.COMPRESSED_AIR.get());
    }

    public static int airVolumeRatio(FluidStack stack) {
        return stack.is(CEProcessFluids.COMPRESSED_AIR.get()) ? Grade.Pressure.PRESSURISED.storageRatio() : 1;
    }

    public static double airVolume(FluidStack stack) {
        return (double) stack.getAmount() / airVolumeRatio(stack);
    }

    public static Component airVolumeLabel(FluidStack stack) {
        return Component.translatable("gas_compression.create_refinery_n_reactors.air_volume",
            BigDecimal.valueOf(airVolume(stack)).stripTrailingZeros().toPlainString());
    }

    public static FluidStack convert(FluidStack input,boolean compress) {
        if(input.isEmpty()) return FluidStack.EMPTY;
        for (var pair : PAIRS) {
            if (!input.is((compress ? pair.low() : pair.high()).get())) continue;
            var output = new FluidStack((compress ? pair.high() : pair.low()).get(), input.getAmount());
            if (isAir(input)) output.applyComponents(input.getComponentsPatch());
            return output;
        }
        return input.copy();
    }
}
