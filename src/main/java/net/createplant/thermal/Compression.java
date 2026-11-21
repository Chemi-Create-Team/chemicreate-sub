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
        new Pair(CEProcessFluids.PRESSURISED_HYDROGEN,CEProcessFluids.COMPRESSED_HYDROGEN),
        new Pair(CEProcessFluids.CARBON_MONOXIDE,CEProcessFluids.PRESSURISED_CARBON_MONOXIDE));
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
        return Grade.Pressure.of(FluidState.bar(FluidState.pressure(stack))).storageRatio();
    }

    public static double airVolume(FluidStack stack) {
        return (double) stack.getAmount() / airVolumeRatio(stack);
    }

    public static Component airVolumeLabel(FluidStack stack) {
        return Component.translatable("gas_compression.create_refinery_n_reactors.air_volume",
            BigDecimal.valueOf(airVolume(stack)).stripTrailingZeros().toPlainString());
    }

    public static PhaseChange.Transfer change(FluidStack input, boolean compress) {
        if(input.isEmpty() || !FluidState.gas(input))return null;
        int current=FluidState.pressure(input);
        int next=FluidState.clamp(current+(compress ? 4 : -4));
        return current==next ? null : PhaseChange.transfer(input,FluidState.temperature(input),next);
    }

    public static FluidStack convert(FluidStack input,boolean compress) {
        var transfer=change(input,compress);
        return transfer==null ? input.copy() : transfer.output(transfer.batches(input.getAmount()));
    }
}
