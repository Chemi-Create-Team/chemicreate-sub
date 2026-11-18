package net.createplant.thermal;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.createplant.registry.CEThermal;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** One discrete grade per ten seconds of uninterrupted heating of the same batch. */
public final class TankHeating {
    public static final int INTERVAL=20, DURATION=200;
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

    public boolean tick(List<FluidStack> fluids,boolean heated) { return tick(fluids, heated, 1); }

    public boolean tick(List<FluidStack> fluids,boolean heated,double efficiency) {
        if(!heated || fluids.stream().noneMatch(f->!f.isEmpty() && FluidHazards.of(f).heat()!=Grade.Heat.VERY_HOT)) {
            ticks=0;batch=List.of();return false;
        }
        if(!sameBatch(fluids)) {ticks=0;remember(fluids);return false;}
        ticks+=INTERVAL*(Double.isFinite(efficiency) ? Math.clamp(efficiency,.5,1.5) : 1);
        if(ticks<DURATION)return false;
        for(var fluid:fluids) if(!fluid.isEmpty()) {
            int heat=FluidHazards.of(fluid).heat().ordinal();
            if(heat<Grade.Heat.VERY_HOT.ordinal())fluid.set(CEThermal.HEAT_GRADE.get(),heat+1);
        }
        ticks-=DURATION;remember(fluids);return true;
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
