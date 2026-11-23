package net.createplant.thermal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Heats the contents of a tank or vessel from the burners under it. Each burner adds its power; every content in the vessel warms
 * by the same number of kelvin, so a heavy or high-heat-capacity content warms slowly, and a boiling content holds at its boiling
 * point until it has taken the latent heat (docs/internal-temperature.md, 4.1).
 */
public final class TankHeating {
    public static final int INTERVAL=10;
    /** Burner power in watts: a lit burner and a superheated one. */
    public static final double LIT_WATTS=1e6, SUPERHEATED_WATTS=2e6;
    /** What a pass did: nothing, only moved the real temperature, or changed the stack (level, phase or amount). */
    public static final int NOTHING=0, KELVIN=1, CHANGED=2;
    private final List<HeatContent> own=new ArrayList<>();

    public static double burnerWatts(Level level,Iterable<BlockPos> base) {
        double watts=0;
        for(var pos:base) {
            var below=pos.below();
            if(!level.hasChunkAt(below))continue;
            var state=level.getBlockState(below);
            if(!(state.getBlock() instanceof BlazeBurnerBlock))continue;
            var heat=state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
            if(heat.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING))watts+=SUPERHEATED_WATTS;
            else if(heat.isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED))watts+=LIT_WATTS;
        }
        return watts;
    }

    public static boolean burnerBelow(Level level,Iterable<BlockPos> base) { return burnerWatts(level,base)>0; }

    /** Energy one pass of {@link #INTERVAL} ticks takes from burners of this power. */
    public static double joules(double watts) { return watts*INTERVAL/20d; }

    public int tick(List<FluidStack> fluids,double watts) { return tick(fluids,watts,ignored->Integer.MAX_VALUE); }

    public int tick(List<FluidStack> fluids,double watts,ToIntFunction<FluidStack> capacity) { return tick(fluids,watts,capacity,List.of()); }

    /** {@code external.get(i)}, where present, is the heat state of {@code fluids.get(i)} that its own inventory already keeps. */
    public int tick(List<FluidStack> fluids,double watts,ToIntFunction<FluidStack> capacity,List<HeatContent> external) {
        while(own.size()<fluids.size())own.add(new HeatContent());
        return watts<=0 ? NOTHING : give(fluids,joules(watts),capacity,external);
    }

    /** Largest temperature change one reaction can make in the contents; the rest of its heat goes into the vessel walls. */
    public static final double REACTION_LIMIT_KELVIN=100;

    /** Adds (or, if negative, takes) the heat of a reaction to everything in the vessel. */
    public int react(List<FluidStack> fluids,double joules,ToIntFunction<FluidStack> capacity,List<HeatContent> external) {
        while(own.size()<fluids.size())own.add(new HeatContent());
        double total=0;
        for(var fluid:fluids)if(!fluid.isEmpty())total+=HeatContent.capacity(fluid);
        double limit=REACTION_LIMIT_KELVIN*total;
        return joules==0 ? NOTHING : give(fluids,Math.clamp(joules,-limit,limit),capacity,external);
    }

    private int give(List<FluidStack> fluids,double joules,ToIntFunction<FluidStack> capacity,List<HeatContent> external) {
        double total=0;
        for(var fluid:fluids)if(!fluid.isEmpty())total+=HeatContent.capacity(fluid);
        if(total<=0)return NOTHING;
        int result=NOTHING;
        for(int i=0;i<fluids.size();i++) {
            var fluid=fluids.get(i);
            if(fluid.isEmpty())continue;
            var heat=i<external.size() && external.get(i)!=null ? external.get(i) : own.get(i);
            double before=heat.kelvin(fluid), beforeQuality=heat.quality(fluid);
            var outcome=heat.add(fluid,joules*HeatContent.capacity(fluid)/total,0,FluidState.kelvin(FluidState.MAX),capacity);
            var next=outcome.stack();
            if(next.getAmount()!=fluid.getAmount() || FluidState.temperature(next)!=FluidState.temperature(fluid) || FluidState.gas(next)!=FluidState.gas(fluid)) {
                fluid.setAmount(next.getAmount());
                fluid.applyComponents(next.getComponentsPatch());
                // A level back at the substance's own state is stored by removing the data, which a patch does not carry.
                FluidState.setTemperature(fluid,FluidState.temperature(next));
                FluidState.setPressure(fluid,FluidState.pressure(next));
                heat.set(fluid,heat.kelvin(next),heat.quality(next));
                result=CHANGED;
            } else if(result==NOTHING && (heat.kelvin(fluid)!=before || heat.quality(fluid)!=beforeQuality))result=KELVIN;
        }
        return result;
    }

    public void write(CompoundTag tag,HolderLookup.Provider lookup) {
        var saved=new ListTag();
        for(var heat:own) {
            var entry=new CompoundTag();
            heat.write(entry);
            saved.add(entry);
        }
        tag.put("HeatContents",saved);
    }

    public void read(CompoundTag tag,HolderLookup.Provider lookup) {
        own.clear();
        var saved=tag.getList("HeatContents",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(saved.size(),16);i++) {
            var heat=new HeatContent();
            heat.read(saved.getCompound(i));
            own.add(heat);
        }
    }
}
