package net.createplant.thermal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.fluids.FluidStack;

public record FluidRequirement(int minTemperature,int maxTemperature,int minPressure,int maxPressure,Phase phase) {
    public enum Phase implements StringRepresentable {
        ANY, LIQUID, GAS;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static final Codec<Integer> LEVEL=Codec.intRange(0,16);
    public static final FluidRequirement ANY=new FluidRequirement(0,16,0,16,Phase.ANY);
    public static final MapCodec<FluidRequirement> CODEC=RecordCodecBuilder.<FluidRequirement>mapCodec(i->i.group(
        LEVEL.optionalFieldOf("min_temperature",0).forGetter(FluidRequirement::minTemperature),
        LEVEL.optionalFieldOf("max_temperature",16).forGetter(FluidRequirement::maxTemperature),
        LEVEL.optionalFieldOf("min_pressure",0).forGetter(FluidRequirement::minPressure),
        LEVEL.optionalFieldOf("max_pressure",16).forGetter(FluidRequirement::maxPressure),
        StringRepresentable.fromEnum(Phase::values).optionalFieldOf("phase",Phase.ANY).forGetter(FluidRequirement::phase)
    ).apply(i,FluidRequirement::new)).validate(range->range.minTemperature<=range.maxTemperature && range.minPressure<=range.maxPressure
        ? DataResult.success(range) : DataResult.error(()->"An upper fluid state bound must be at least its lower bound"));

    public boolean matches(FluidStack stack) {
        if(stack.isEmpty())return false;
        int temperature=FluidState.temperature(stack), pressure=FluidState.pressure(stack);
        return temperature>=minTemperature && temperature<=maxTemperature && pressure>=minPressure && pressure<=maxPressure
            && (phase==Phase.ANY || FluidState.gas(stack)==(phase==Phase.GAS));
    }

    public FluidStack example(FluidStack nominal) {
        if(matches(nominal))return nominal;
        int defaultTemp=FluidState.temperature(nominal), defaultPressure=FluidState.pressure(nominal);
        int bestDistance=Integer.MAX_VALUE;
        FluidStack best=FluidStack.EMPTY;
        for(int pressure=minPressure;pressure<=maxPressure;pressure++)for(int temp=minTemperature;temp<=maxTemperature;temp++) {
            int distance=Math.abs(defaultTemp-temp)+Math.abs(defaultPressure-pressure);
            if(distance>=bestDistance || phase!=Phase.ANY && FluidState.gas(nominal,temp,pressure)!=(phase==Phase.GAS))continue;
            bestDistance=distance;
            best=FluidState.with(nominal,temp,pressure);
        }
        return best;
    }
}
