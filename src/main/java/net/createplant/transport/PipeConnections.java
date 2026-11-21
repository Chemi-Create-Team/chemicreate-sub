package net.createplant.transport;

import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.AxisPipeBlock;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Each closed joint is stored once, on its negative-axis endpoint, in this dimension's saved data. */
public final class PipeConnections extends SavedData {
    private static final String FILE = "createplant_pipe_connections";
    private static final Factory<PipeConnections> FACTORY = new Factory<>(PipeConnections::new, PipeConnections::load);
    private static final Direction[] SIDES = Direction.values();
    private final Long2ByteOpenHashMap closed = new Long2ByteOpenHashMap();

    private static PipeConnections get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, FILE); }

    public static boolean pipe(BlockState state) {
        return state.getBlock() instanceof FluidPipeBlock || state.getBlock() instanceof EncasedPipeBlock
            || state.getBlock() instanceof GlassFluidPipeBlock;
    }

    private static boolean loaded(BlockGetter world, BlockPos pos) {
        return !(world instanceof Level level) || level.hasChunkAt(pos);
    }

    public static boolean isClosed(BlockGetter world, BlockPos pos, Direction side) {
        if (world instanceof ServerLevel server) return get(server).contains(pos, side);
        if (world == null || world instanceof Level level && !level.isClientSide) return false;
        return clientClosed(world, pos, side) || clientClosed(world, pos.relative(side), side.getOpposite());
    }

    private static boolean clientClosed(BlockGetter world, BlockPos pos, Direction side) {
        if (!loaded(world, pos)) return false;
        var sync = BlockEntityBehaviour.get(world, pos, Sync.TYPE);
        return sync != null && (sync.mask & faceBit(side)) != 0;
    }

    private static int bit(Direction side) { return 1 << side.getAxis().ordinal(); }
    private static int faceBit(Direction side) { return 1 << side.get3DDataValue(); }

    private static long address(BlockPos pos, Direction face) {
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.asLong() : pos.relative(face).asLong();
    }

    private boolean contains(BlockPos pos, Direction side) {
        return !closed.isEmpty() && (closed.get(address(pos, side)) & bit(side)) != 0;
    }

    private void set(BlockPos pos, Direction side, boolean cut) {
        long key = address(pos, side);
        byte before = closed.get(key);
        byte next = (byte) (cut ? before | bit(side) : before & ~bit(side));
        if (next == before) return;
        if (next == 0) closed.remove(key);else closed.put(key, next);
        setDirty();
    }

    public static BlockState apply(BlockGetter world, BlockPos pos, BlockState state) {
        for (Direction side : SIDES) {
            var property = FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side);
            if (state.hasProperty(property) && state.getValue(property) && isClosed(world, pos, side))
                state = state.setValue(property, false);
        }
        return state;
    }

    public static boolean canToggle(Level world, BlockPos pos, Direction side) {
        BlockPos other = pos.relative(side);
        if (!loaded(world, pos) || !loaded(world, other) || !world.getWorldBorder().isWithinBounds(other)) return false;
        var here = world.getBlockState(pos);
        var there = world.getBlockState(other);
        if (!pipe(here) || !pipe(there) || BlockEntityBehaviour.get(world, pos, Sync.TYPE) == null
                || BlockEntityBehaviour.get(world, other, Sync.TYPE) == null) return false;
        if (isClosed(world, pos, side)) return permitsAxis(here, side) && permitsAxis(there, side);
        var first = FluidPropagator.getPipe(world, pos);
        var second = FluidPropagator.getPipe(world, other);
        return first != null && second != null && first.canHaveFlowToward(here, side) && second.canHaveFlowToward(there, side.getOpposite());
    }

    private static boolean permitsAxis(BlockState state, Direction side) {
        return !(state.getBlock() instanceof GlassFluidPipeBlock) || state.getValue(AxisPipeBlock.AXIS) == side.getAxis();
    }

    public static boolean toggle(ServerLevel world, BlockPos pos, Direction side) {
        if (!canToggle(world, pos, side)) return false;
        var other = pos.relative(side);
        var data = get(world);
        boolean cut = !data.contains(pos, side);
        data.set(pos, side, cut);
        updateFace(world, pos, side, !cut);
        updateFace(world, other, side.getOpposite(), !cut);
        // Rebuild from each half after both states are committed; the old path can no longer reach the other half.
        for (BlockPos end : List.of(pos, other)) {
            FluidPropagator.propagateChangedPipe(world, end, world.getBlockState(end));
            if (world.getBlockEntity(end) instanceof SmartBlockEntity pipe) pipe.notifyUpdate();
        }
        return true;
    }

    private static void updateFace(ServerLevel world, BlockPos pos, Direction side, boolean open) {
        BlockState state = world.getBlockState(pos);
        var property = FluidPipeBlock.PROPERTY_BY_DIRECTION.get(side);
        if (state.hasProperty(property) && state.getValue(property) != open)
            world.setBlock(pos, state.setValue(property, open), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
    }

    private static PipeConnections load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data = new PipeConnections();
        for (Tag entry : tag.getList("Closed", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) entry;
            byte mask = (byte) (row.getByte("Axes") & 7);
            if (mask != 0) data.closed.put(row.getLong("Pos"), mask);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        var rows = new ListTag();
        for (var entry : closed.long2ByteEntrySet()) {
            var row = new CompoundTag();
            row.putLong("Pos", entry.getLongKey());
            row.putByte("Axes", entry.getByteValue());
            rows.add(row);
        }
        tag.put("Closed", rows);
        return tag;
    }

    public static void attach(BlockEntityBehaviourEvent event) {
        for (var type : List.of(AllBlockEntityTypes.FLUID_PIPE.get(), AllBlockEntityTypes.ENCASED_FLUID_PIPE.get(),
                AllBlockEntityTypes.GLASS_FLUID_PIPE.get(), CETransport.STAINLESS_PIPE_ENTITY.get(),
                CETransport.STAINLESS_ENCASED_PIPE_ENTITY.get(), CETransport.STAINLESS_GLASS_PIPE_ENTITY.get(),
                CETransport.PIPE.get(), CEBlockEntityTypes.RESIN_PIPE.get()))
            event.forType(type, pipe -> event.attach(new Sync(pipe)));
    }

    private static final class Sync extends BlockEntityBehaviour {
        private static final BehaviourType<Sync> TYPE = new BehaviourType<>();
        private int mask;

        private Sync(SmartBlockEntity pipe) { super(pipe); }
        @Override public BehaviourType<?> getType() { return TYPE; }

        @Override public void initialize() {
            super.initialize();
            if (!(getWorld() instanceof ServerLevel server)) return;
            var state = blockEntity.getBlockState();
            if (!(state.getBlock() instanceof EncasedPipeBlock)) return;
            var sealed = apply(server, getPos(), state);
            if (sealed != state) server.setBlock(getPos(), sealed, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }

        @Override public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
            if (!packet || !(getWorld() instanceof ServerLevel server)) return;
            var data = PipeConnections.get(server);
            int next = 0;
            for (Direction side : SIDES) if (data.contains(getPos(), side)) next |= faceBit(side);
            if (next != 0) tag.putByte("ClosedPipeFaces", (byte) next);
        }

        @Override public void read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
            if (!packet) return;
            int next = tag.getByte("ClosedPipeFaces") & 63;
            if (next == mask) return;
            mask = next;
            if (getWorld() != null && getWorld().isClientSide) {
                blockEntity.requestModelDataUpdate();
                BlockState state = blockEntity.getBlockState();
                getWorld().sendBlockUpdated(getPos(), state, state, Block.UPDATE_CLIENTS);
            }
        }
    }
}
