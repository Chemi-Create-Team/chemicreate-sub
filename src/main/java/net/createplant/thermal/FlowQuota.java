package net.createplant.thermal;

import java.util.*;
import net.createplant.block.entity.FlowRegulatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Reservations are per native simulation; actual budgets are shared by every source network. */
public final class FlowQuota {
    private FlowQuota() { }
    public static int remaining(Level level,List<BlockPos> path,Map<BlockPos,Integer> reserved,int requested) {
        int n=requested;
        for (BlockPos pos:path) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FlowRegulatorBlockEntity valve)
            n=Math.min(n,Math.max(0,valve.remaining()-reserved.getOrDefault(pos,0)));
        return n;
    }
    public static void accept(Level level,List<BlockPos> path,Map<BlockPos,Integer> reserved,int n,boolean execute) {
        for (BlockPos pos:path) if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FlowRegulatorBlockEntity valve) {
            if (execute) valve.consume(n);else reserved.merge(pos,n,Integer::sum);
        }
    }
}
