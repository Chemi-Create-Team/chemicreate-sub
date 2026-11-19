package net.createplant.block.entity;

import java.util.List;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createplant.registry.CEGasPipes;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CETransport;
import net.createplant.thermal.Grade;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Recent committed gas throughput. Only the audible bands are sent to tracking clients. */
public final class PipeGasFlow extends BlockEntityBehaviour {
    public static final BehaviourType<PipeGasFlow> TYPE = new BehaviourType<>();
    private static final int SYNC_INTERVAL = 8;
    private static final int SILENCE_DELAY = 10;
    private long observed = Long.MIN_VALUE / 2;
    private int volume, grade, synced;

    private PipeGasFlow(SmartBlockEntity pipe) { super(pipe); }

    public static void attach(BlockEntityBehaviourEvent event) {
        for (var type : List.of(AllBlockEntityTypes.FLUID_PIPE.get(), AllBlockEntityTypes.GLASS_FLUID_PIPE.get(),
                AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(), AllBlockEntityTypes.SMART_FLUID_PIPE.get(), CEGasPipes.GAS_PIPE_ENTITY.get(),
                CEBlockEntityTypes.RESIN_PIPE.get(), CETransport.PIPE.get(), CETransport.STAINLESS_PIPE_ENTITY.get(),
                CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get(), CETransport.STAINLESS_GLASS_PIPE_ENTITY.get()))
            event.forType(type, pipe -> event.attach(new PipeGasFlow(pipe)));
    }

    @Override public BehaviourType<?> getType() { return TYPE; }
    public int pressureBand() { return synced >> 3; }
    public int flowBand() { return synced & 7; }

    public void observe(long time, Grade.Pressure pressure, int amount) {
        if (observed != time) {
            observed = time;
            volume = grade = 0;
        }
        grade = Math.max(grade, pressure.ordinal());
        volume += Math.min(128 - volume, amount);
    }

    @Override public void tick() {
        var level = getWorld();
        if (level == null || level.isClientSide || (synced == 0 && volume == 0)) return;
        long time = level.getGameTime();
        var pos = getPos();
        if (Math.floorMod(time + pos.getX() + pos.getY() + pos.getZ(), SYNC_INTERVAL) != 0) return;
        int next = 0;
        if (time - observed < SILENCE_DELAY && volume > 0) {
            int band = volume < 8 ? 1 : volume < 32 ? 2 : volume < 128 ? 3 : 4;
            next = grade << 3 | band;
        } else {
            volume = 0;
        }
        if (next != synced) {
            synced = next;
            blockEntity.sendData();
        }
    }

    @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        if (packet && synced != 0) tag.putByte("GasFlow", (byte) synced);
    }

    @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        if (packet) synced = tag.getByte("GasFlow") & 31;
    }
}
