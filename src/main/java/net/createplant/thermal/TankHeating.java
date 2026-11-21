package net.createplant.thermal;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** One temperature level every fifty ticks of uninterrupted heating of the same batch. */
public final class TankHeating {
    public static final int INTERVAL=10, DURATION=50;
    private double ticks;
    private List<FluidStack> batch=List.of();

    public static boolean burnerBelow(Level level,Iterable<BlockPos> base) {
        for(var pos:base) {
            var below=pos.below();
            if(!level.hasChunkAt(below))continue;
            var state=level.getBlockState(below);
            if(state.getBlock() instanceof BlazeBurnerBlock && state.getValue(BlazeBurnerBlock.HEAT_LEVEL).isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED))return true;
        }
        return false;
    }

    public int progress() { return (int) ticks; }

    public boolean tick(List<FluidStack> fluids,boolean heated) {
        return tick(fluids,heated,ignored->Integer.MAX_VALUE);
    }

    public boolean tick(List<FluidStack> fluids,boolean heated,java.util.function.ToIntFunction<FluidStack> capacity) {
        if(!heated || fluids.stream().noneMatch(f->!f.isEmpty() && FluidState.temperature(f)<FluidState.MAX)) {
            ticks=0;batch=List.of();return false;
        }
        if(!sameBatch(fluids)) {ticks=0;remember(fluids);}
        ticks+=INTERVAL;
        if(ticks<DURATION)return false;
        boolean changed=false;
        for(var fluid:fluids) if(!fluid.isEmpty()) {
            int temperature=FluidState.temperature(fluid);
            if(temperature==FluidState.MAX)continue;
            var change=PhaseChange.transfer(fluid,temperature+1,FluidState.pressure(fluid));
            int batches=change.batches(fluid.getAmount());
            if(batches*change.consumed()!=fluid.getAmount() || (long)batches*change.produced()>capacity.applyAsInt(change.prototype()))continue;
            fluid.setAmount(batches*change.produced());
            fluid.applyComponents(change.prototype().getComponentsPatch());
            // A level back at the substance's own state is stored by removing the data, which a patch does not carry.
            FluidState.setTemperature(fluid,FluidState.temperature(change.prototype()));
            FluidState.setPressure(fluid,FluidState.pressure(change.prototype()));
            changed=true;
        }
        ticks-=DURATION;
        remember(fluids);
        return changed;
    }

    private boolean sameBatch(List<FluidStack> fluids) {
        if(batch.size()!=fluids.size())return false;
        for(int i=0;i<batch.size();i++) {
            var old=batch.get(i);var next=fluids.get(i);
            if(old.isEmpty() && next.isEmpty())continue;
            if(!FluidThermal.compatible(old,next))return false;
        }
        return true;
    }

    private void remember(List<FluidStack> fluids) {
        batch=new ArrayList<>(fluids.size());
        for(var fluid:fluids)batch.add(fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1));
    }

    public void write(CompoundTag tag,HolderLookup.Provider lookup) {
        tag.putDouble("HeatingTicks",ticks);
        var saved=new ListTag();
        for(var fluid:batch)saved.add(fluid.isEmpty() ? new CompoundTag() : fluid.save(lookup));
        tag.put("HeatingBatch",saved);
    }

    public void read(CompoundTag tag,HolderLookup.Provider lookup) {
        ticks=Double.isFinite(tag.getDouble("HeatingTicks")) ? Math.clamp(tag.getDouble("HeatingTicks"),0,DURATION-.001) : 0;
        batch=new ArrayList<>();
        var saved=tag.getList("HeatingBatch",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(saved.size(),16);i++)batch.add(FluidStack.parseOptional(lookup,saved.getCompound(i)));
    }
}
