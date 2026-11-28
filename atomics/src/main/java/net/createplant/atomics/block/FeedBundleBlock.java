package net.createplant.atomics.block;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.createplant.atomics.block.entity.FeedBundleBlockEntity;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.nuclear.EvenShare;
import net.createplant.atomics.nuclear.ReactorCores;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A bundle of thin feed-water tubes under the core. A Create pipe can feed water through any exposed face of any connected bundle;
 * the bundle network shares it fairly among the fuel columns above it.
 */
public class FeedBundleBlock extends Block implements EntityBlock {
    /** More than the largest supported 13 x 13 x 7 bundle bed, while still bounding malformed networks. */
    private static final int BUNDLE_LIMIT = 2048;

    public FeedBundleBlock(Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FeedBundleBlockEntity(pos, state); }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
            (level, pos, state, blockEntity, side) -> new FeedHandler(level, pos), CENuclear.FEED_BUNDLE.get());
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(state.getBlock())) ReactorCores.touched(level);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!newState.is(state.getBlock())) ReactorCores.touched(level);
    }

    /** The port is a capability on the block; the empty block entity only lets Create's pipes see it. */
    private static final class FeedHandler implements IFluidHandler {
        private final Level level;
        private final BlockPos origin;
        private List<BlockPos> columns = List.of();
        private Set<BlockPos> loadedChunks = Set.of(), unloadedChunks = Set.of();
        private long version = Long.MIN_VALUE;
        private int cursor;

        private FeedHandler(Level level, BlockPos origin) {
            this.level = level;
            this.origin = origin.immutable();
        }

        private BlockPos chunkProbe(BlockPos pos) {
            return new BlockPos(pos.getX() & ~15, origin.getY(), pos.getZ() & ~15);
        }

        private List<BlockPos> columns() {
            long current = ReactorCores.version(level);
            if (current == version && loadedChunks.stream().allMatch(level::isLoaded)
                && unloadedChunks.stream().noneMatch(level::isLoaded)) return columns;
            version = current;

            Set<BlockPos> bundles = new HashSet<>();
            Set<BlockPos> loaded = new HashSet<>(), unloaded = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(origin);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.removeFirst();
                if (bundles.contains(pos)) continue;
                if (!level.isLoaded(pos)) {
                    unloaded.add(chunkProbe(pos));
                    continue;
                }
                loaded.add(chunkProbe(pos));
                if (!level.getBlockState(pos).is(CENuclear.FEED_BUNDLE.get())) continue;
                if (bundles.size() >= BUNDLE_LIMIT) {
                    loadedChunks = Set.copyOf(loaded);
                    unloadedChunks = Set.copyOf(unloaded);
                    columns = List.of();
                    return columns;
                }
                bundles.add(pos.immutable());
                for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
            }

            Set<BlockPos> found = new HashSet<>();
            for (BlockPos bundle : bundles) {
                BlockPos above = bundle.above();
                if (level.getBlockEntity(above) instanceof FuelChannelBlockEntity) found.add(above.immutable());
            }
            loadedChunks = Set.copyOf(loaded);
            unloadedChunks = Set.copyOf(unloaded);
            columns = found.stream().sorted().toList();
            return columns;
        }

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) {
            if (tank != 0) return 0;
            long capacity = 0;
            for (BlockPos pos : columns())
                if (level.getBlockEntity(pos) instanceof FuelChannelBlockEntity column)
                    capacity += column.inlet().getTankCapacity(0);
            return (int) Math.min(Integer.MAX_VALUE, capacity);
        }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank == 0 && net.createplant.atomics.nuclear.ReactorCoolant.accepts(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) {
            if (!net.createplant.atomics.nuclear.ReactorCoolant.accepts(stack)) return 0;
            List<BlockPos> available = columns();
            if (available.isEmpty()) return 0;
            int n = available.size();
            var probe = stack.copyWithAmount(Integer.MAX_VALUE);
            var inlets = new IFluidHandler[n];
            int[] room = new int[n];
            for (int i = 0; i < n; i++)
                if (level.getBlockEntity(available.get(i)) instanceof FuelChannelBlockEntity column) {
                    inlets[i] = column.inlet();
                    room[i] = inlets[i].fill(probe, FluidAction.SIMULATE);
                }
            int start = Math.floorMod(cursor, n), filled = 0;
            int[] share = EvenShare.split(stack.getAmount(), room, start);
            for (int i = 0; i < n; i++) if (share[i] > 0) filled += inlets[i].fill(stack.copyWithAmount(share[i]), action);
            if (filled > 0 && action.execute()) cursor = (start + filled) % n;
            return filled;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }
}
