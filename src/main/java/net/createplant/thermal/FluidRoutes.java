package net.createplant.thermal;

import java.util.*;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.createmod.catnip.math.BlockFace;
import net.createplant.block.entity.PipeGasFlow;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.createplant.transport.FluidHazards;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;

/** One directed representative tree per native source network, never a second fluid network. */
public final class FluidRoutes {
    private static final Map<Level,Long> EPOCHS=Collections.synchronizedMap(new WeakHashMap<>());
    public static long builds,transfers;
    private final Map<BlockPos,BlockPos> parents=new HashMap<>();
    private final Map<BlockFace,List<BlockPos>> routes=new HashMap<>();
    private final Set<BlockPos> ambiguous=new HashSet<>();
    private final Set<BlockPos> valves=new HashSet<>();
    private final Map<List<BlockPos>,List<BlockPos>> controls=new IdentityHashMap<>();
    public List<BlockPos> controls(List<BlockPos> path) { return controls.computeIfAbsent(path,key->key.stream().filter(valves::contains).toList()); }
    private long epoch=-1;
    private int count;
    public long rebuilds;
    private long builtAt=-100;
    public static void invalidateAll() { EPOCHS.replaceAll((world,epoch)->epoch+1); }
    public static long stamp(Level level) { return EPOCHS.getOrDefault(level,0L); }
    public static void changed(Level level) { if (!level.isClientSide) EPOCHS.merge(level,1L,Long::sum); }
    public void clear() { epoch=-1;builtAt=-100;ambiguous.clear();parents.clear();routes.clear();controls.clear();valves.clear(); }
    public List<BlockPos> path(Level level,BlockFace start,BlockFace target) {
        long current=EPOCHS.getOrDefault(level,0L);
        if (current!=epoch) { clear();epoch=current; }
        if (!parents.containsKey(target.getPos()) && level.getGameTime()-builtAt>=20) build(level,start);
        if (!parents.containsKey(target.getPos())) return List.of();
        return routes.computeIfAbsent(target,key->{
            List<BlockPos> path=new ArrayList<>();BlockPos at=key.getPos();
            while (at!=null) { path.add(at);at=parents.get(at); }
            Collections.reverse(path);return List.copyOf(path);
        });
    }
    private void build(Level level,BlockFace start) {
        ambiguous.clear();parents.clear();routes.clear();controls.clear();valves.clear();builds++;rebuilds++;builtAt=level.getGameTime();count=0;
        Queue<BlockPos> queue=new ArrayDeque<>();BlockPos root=start.getPos();parents.put(root,null);queue.add(root);
        while (!queue.isEmpty() && count++<4096) {
            BlockPos at=queue.remove();if (!level.hasChunkAt(at)) continue;
            if (level.getBlockEntity(at) instanceof net.createplant.block.entity.FlowRegulatorBlockEntity) valves.add(at);
            FluidTransportBehaviour pipe=FluidPropagator.getPipe(level,at);if (pipe==null) continue;
            int inputs=0;for (Direction side:Direction.values()) {var flow=pipe.getFlow(side);if (flow!=null && flow.inbound && flow.complete) inputs++;}
            if (inputs>1) ambiguous.add(at);
            for (Direction side:Direction.values()) {
                var flow=pipe.getFlow(side);
                if (flow==null || flow.inbound || !flow.complete) continue;
                BlockPos next=at.relative(side);if (parents.containsKey(next) || !level.hasChunkAt(next)) continue;
                FluidTransportBehaviour other=FluidPropagator.getPipe(level,next);
                if (other==null || !other.canHaveFlowToward(level.getBlockState(next),side.getOpposite())) continue;
                var incoming=other.getFlow(side.getOpposite());
                if (incoming==null || !incoming.inbound || !incoming.complete) continue;
                parents.put(next,at);queue.add(next);
            }
        }
        if (!queue.isEmpty()) { ambiguous.clear();parents.clear(); }
    }
    public void observe(Level level,List<BlockPos> path,net.neoforged.neoforge.fluids.FluidStack fluid) {
        if (path.isEmpty()) return;
        var properties = FluidHazards.of(fluid);
        var pressure = properties.pressure();
        boolean audible = pressure != Grade.Pressure.ATMOSPHERIC && properties.gas();
        boolean highPressureSteam = pressure.compareTo(Grade.Pressure.HIGH)>=0 && SteamGrades.of(fluid) != null;
        for (var at : path) {
            if (!level.hasChunkAt(at)) continue;
            var be = level.getBlockEntity(at);
            if (be instanceof PipeGaugeBlockEntity gauge) {
                if (gauge.kind() == net.createplant.block.PipeGaugeBlock.Kind.FLOW) gauge.observeFlow(fluid.getAmount(), 2);
                else gauge.observe(fluid);
            } else if (audible && be instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity pipe) {
                var sound = pipe.getBehaviour(PipeGasFlow.TYPE);
                if (sound != null) {
                    sound.observe(level.getGameTime(), pressure, fluid.getAmount());
                    if (highPressureSteam) sound.observeHighPressureSteam(level.getGameTime());
                }
            }
        }
    }
}
