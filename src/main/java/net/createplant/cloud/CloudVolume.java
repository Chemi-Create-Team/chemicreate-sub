package net.createplant.cloud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CloudVolume {
    private static final int WIDTH = 17, HEIGHT = 9, LIMIT = 512;
    public static final int HORIZONTAL_REACH = WIDTH / 2, VERTICAL_REACH = HEIGHT / 2;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ConcurrentHashMap<BlockState, Integer> OCCLUSION = new ConcurrentHashMap<>();

    private final BlockPos origin;
    private final BitSet cells;
    private final AABB bounds;

    private CloudVolume(BlockPos origin, BitSet cells) {
        this.origin = origin.immutable();
        this.cells = cells;
        AABB bounds = null;
        for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1)) {
            var cell = new AABB(position(i));
            bounds = bounds == null ? cell : bounds.minmax(cell);
        }
        this.bounds = bounds == null ? new AABB(origin) : bounds;
    }

    static CloudVolume empty(BlockPos origin) { return new CloudVolume(origin, new BitSet()); }

    public static CloudVolume build(Level level, BlockPos origin, int amount, boolean ceiling) {
        int target = Math.min(LIMIT, 8 + Math.max(0, amount) / 20);
        var ordering = Comparator.comparingInt((BlockPos pos) -> ceiling ? -pos.getY() : pos.getY())
            .thenComparingDouble(origin::distSqr).thenComparing(Comparator.naturalOrder());
        var pending = new PriorityQueue<>(ordering);
        var visited = new BitSet(WIDTH * WIDTH * HEIGHT);
        var filled = new BitSet(WIDTH * WIDTH * HEIGHT);
        if (open(level, origin)) {
            pending.add(origin);
            visited.set(index(origin, origin));
        } else {
            for (var side : DIRECTIONS) {
                var next = origin.relative(side);
                if (!passable(level, next, side.getAxis())) continue;
                pending.add(next);
                visited.set(index(origin, next));
            }
        }

        for (int count = 0; count < target && !pending.isEmpty(); count++) {
            var pos = pending.remove();
            filled.set(index(origin, pos));
            for (var direction : DIRECTIONS) {
                var next = pos.relative(direction);
                int index = index(origin, next);
                if (index < 0 || visited.get(index) || !connected(level, pos, next, direction)) continue;
                visited.set(index);
                pending.add(next);
            }
        }
        return new CloudVolume(origin, filled);
    }

    public static Set<BlockPos> reachable(Level level, BlockPos origin, int reach) {
        var cells = new HashSet<BlockPos>();
        var pending = new ArrayDeque<BlockPos>();
        boolean originOpen = open(level, origin);
        if (originOpen) {
            cells.add(origin);
            pending.add(origin);
        } else {
            for (var side : DIRECTIONS) {
                var next = origin.relative(side);
                if (passable(level, next, side.getAxis()) && cells.add(next)) pending.add(next);
            }
        }
        while (!pending.isEmpty()) {
            var pos = pending.remove();
            for (var side : DIRECTIONS) {
                var next = pos.relative(side);
                if (origin.distSqr(next) > reach * reach || cells.contains(next) || !connected(level, pos, next, side)) continue;
                cells.add(next);
                pending.add(next);
            }
        }
        return cells;
    }

    private static boolean connected(Level level, BlockPos from, BlockPos to, Direction direction) {
        return passable(level, from, direction.getAxis()) && passable(level, to, direction.getAxis());
    }

    private static boolean open(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && mask(state) != 7;
    }

    private static boolean passable(Level level, BlockPos pos, Direction.Axis axis) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (mask(state) & 1 << axis.ordinal()) == 0;
    }

    private static int mask(BlockState state) {
        if (state.isAir()) return 0;
        return OCCLUSION.computeIfAbsent(state, CloudVolume::occlusion);
    }

    private static int occlusion(BlockState state) {
        var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return 0;
        var boxes = shape.toAabbs();
        int mask = 0;
        for (var axis : Direction.Axis.values()) {
            VoxelShape projected = Shapes.empty();
            for (var box : boxes) {
                var projection = switch (axis) {
                    case X -> Shapes.box(0, box.minY, box.minZ, 1, box.maxY, box.maxZ);
                    case Y -> Shapes.box(box.minX, 0, box.minZ, box.maxX, 1, box.maxZ);
                    case Z -> Shapes.box(box.minX, box.minY, 0, box.maxX, box.maxY, 1);
                };
                projected = Shapes.or(projected, projection);
            }
            if (!Shapes.joinIsNotEmpty(Shapes.block(), projected, BooleanOp.ONLY_FIRST)) mask |= 1 << axis.ordinal();
        }
        return mask;
    }

    private static int index(BlockPos origin, BlockPos pos) {
        int x = pos.getX() - origin.getX() + HORIZONTAL_REACH;
        int y = pos.getY() - origin.getY() + VERTICAL_REACH;
        int z = pos.getZ() - origin.getZ() + HORIZONTAL_REACH;
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < WIDTH ? (y * WIDTH + z) * WIDTH + x : -1;
    }

    private BlockPos position(int index) {
        return origin.offset(index % WIDTH - HORIZONTAL_REACH, index / (WIDTH * WIDTH) - VERTICAL_REACH,
            index / WIDTH % WIDTH - HORIZONTAL_REACH);
    }

    public boolean contains(BlockPos pos) {
        int index = index(origin, pos);
        return index >= 0 && cells.get(index);
    }

    public boolean contains(Vec3 point, float topHeight) {
        return point.y < bounds.maxY - 1 + topHeight && contains(BlockPos.containing(point));
    }

    public BlockPos nearest(BlockPos origin, Set<BlockPos> reachable) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : reachable) {
            double candidate = origin.distSqr(pos);
            if (candidate < distance && contains(pos)) {
                best = pos;
                distance = candidate;
            }
        }
        return best;
    }

    public List<BlockPos> positions() {
        var list = new ArrayList<BlockPos>(size());
        for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1)) list.add(position(i));
        return list;
    }

    public boolean seesSky(Level level) {
        for (int i = cells.nextSetBit(0); i >= 0; i = cells.nextSetBit(i + 1))
            if (level.canSeeSky(position(i))) return true;
        return false;
    }

    public BlockPos origin() { return origin; }
    public int size() { return cells.cardinality(); }
    public AABB bounds() { return bounds; }

    public boolean sameCells(CloudVolume other) { return other != null && origin.equals(other.origin) && cells.equals(other.cells); }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Origin", origin.asLong());
        tag.putLongArray("Cells", cells.toLongArray());
        return tag;
    }

    public static CloudVolume load(CompoundTag tag) {
        var raw = tag.getLongArray("Cells");
        int max = WIDTH * WIDTH * HEIGHT;
        var bits = raw.length > (max + 63) / 64 ? new BitSet() : BitSet.valueOf(raw);
        if (bits.length() > max || bits.cardinality() > LIMIT) bits.clear();
        return new CloudVolume(BlockPos.of(tag.getLong("Origin")), bits);
    }
}
