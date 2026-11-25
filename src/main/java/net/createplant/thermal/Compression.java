package net.createplant.thermal;

import java.math.BigDecimal;
import java.util.List;
import net.createplant.registry.CEFluids;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

/** One pressure-grade step, shared by compressors and reducing valves. Gas quantities are stored in atmospheric-equivalent mB. */
public final class Compression {
    /** One compression step: 1 mB of the fluid at the lower state and at the state the compressor makes of it. */
    public record Step(FluidStack low, FluidStack high) { }
    private static List<Step> steps;
    private Compression() { }
    /** Each low-pressure state with the state one compression step makes of it, in compressor order. */
    public static synchronized List<Step> steps() {
        if(steps==null)steps=List.of(
            new Step(new FluidStack(CEFluids.AIR_GAS.get(),1),FluidForm.COMPRESSED_AIR.stack(1)),
            new Step(new FluidStack(CEProcessFluids.SYNTHESIS_GAS.get(),1),FluidForm.COMPRESSED_SYNTHESIS_FEED.stack(1)),
            new Step(new FluidStack(CEFluids.HYDROGEN_GAS.get(),1),FluidForm.PRESSURISED_HYDROGEN.stack(1)),
            new Step(FluidForm.PRESSURISED_HYDROGEN.stack(1),FluidForm.COMPRESSED_HYDROGEN.stack(1)),
            new Step(new FluidStack(CEProcessFluids.CARBON_MONOXIDE.get(),1),FluidForm.PRESSURISED_CARBON_MONOXIDE.stack(1)));
        return steps;
    }
    public static boolean isAir(FluidStack stack) {
        return stack.is(CEFluids.AIR_GAS.get());
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
