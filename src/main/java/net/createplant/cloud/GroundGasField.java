package net.createplant.cloud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class GroundGasField {
    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP
    };
    private static final int UP = 32;
    private static final int OPEN_AIR_TICKS = 480, SHELTERED_TICKS = 1560, POOL_FILL_TICKS = 24;
    private static final int POOL_AREA_LIMIT = 64, POOL_LAYER_CELL_MB = 1_000;
    public static final float MIN_CONCENTRATION = .018f;
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final Map<Long, Section> sections = new HashMap<>();
    private final Map<BlockPos, Arrival> leading = new HashMap<>();
    private final PriorityQueue<Arrival> arrivals = new PriorityQueue<>(Comparator.comparingInt(Arrival::tick)
        .thenComparing(Comparator.comparingDouble(Arrival::density).reversed()));
    private final BlockPos origin;
    private final long startedAt;
    private final LongConsumer sectionOpened;
    private final LongConsumer sectionClosed;
    private final PriorityQueue<Expiry> expiries = new PriorityQueue<>(Comparator.comparingInt(Expiry::tick));
    private final PriorityQueue<Rise> rises = new PriorityQueue<>(Comparator.comparingInt(Rise::tick));
    private long lastRelease = Long.MIN_VALUE;
    private long observedReleaseAmount, remainingPoolAmount;
    private float lastCharge;

    public static final class Cell {
        final BlockPos pos;
        final AABB bounds;
        final int floor;
        final boolean canFall;
        int openings, arrivedAt, dilutionTicks;
        float density, previousDensity;
        boolean pooled, riseQueued;
        int expiresAt;

        public BlockPos pos() { return pos; }
        public AABB bounds() { return bounds; }
        public int floor() { return floor; }
        public int openings() { return openings; }
        public int arrivedAt() { return arrivedAt; }
        public boolean inFreeFall() { return canFall && !pooled; }
        public int ceiling(float opacity) {
            return (openings & UP) != 0 ? 16 : Math.round(floor + (16 - floor) * (.28f + .72f * opacity));
        }

        Cell(Level level, BlockPos pos) {
            this.pos = pos;
            bounds = new AABB(pos);
            canFall = CloudVolume.canFlow(level, pos, Direction.DOWN);
            dilutionTicks = dilutionTicks(level, pos);
            int floor = 0;
            var state = level.getBlockState(pos);
            if (!state.isAir()) {
                for (var box : state.getCollisionShape(level, pos).toAabbs())
                    if (box.minY == 0 && box.minX == 0 && box.maxX == 1 && box.minZ == 0 && box.maxZ == 1)
                        floor = Math.max(floor, Mth.floor(box.maxY * 16));
            }
            this.floor = floor;
        }

        float remaining(float time) {
            return Math.max(0, density - (time - arrivedAt) / dilutionTicks);
        }

        public float opacity(float time) {
            return Math.max(0, remaining(time) - (density - previousDensity) * (1 - fade((time - arrivedAt) / 20)));
        }
    }

    public static final class Section {
        final AABB bounds;
        final Map<BlockPos, Cell> cells = new HashMap<>();

        public AABB bounds() { return bounds; }
        public Collection<Cell> cells() { return cells.values(); }

        Section(BlockPos pos) {
            var min = SectionPos.of(pos).origin();
            bounds = new AABB(min).expandTowards(15, 15, 15);
        }
    }

    private record Arrival(BlockPos pos, Direction direction, int tick, float density, int dilutionTicks) { }

    private record Expiry(Cell cell, int tick) { }

    private record Rise(Cell cell, int tick) { }

    public GroundGasField(BlockPos origin, long startedAt, LongConsumer sectionOpened, LongConsumer sectionClosed) {
        this.origin = origin.immutable();
        this.startedAt = startedAt;
        this.sectionOpened = sectionOpened;
        this.sectionClosed = sectionClosed;
    }

    public GroundGasField(BlockPos origin, long startedAt) {
        this(origin, startedAt, key -> { }, key -> { });
    }

    public void preview(Level level, CloudVolume volume, Predicate<BlockPos> inside) {
        for (var pos : volume.positions()) {
            if (!inside.test(pos)) continue;
            var cell = add(level, pos);
            cell.arrivedAt = pos.distManhattan(origin) * 6;
        }
    }

    public BlockPos origin() { return origin; }
    public Collection<Section> sections() { return sections.values(); }
    public boolean isEmpty() { return cells.isEmpty() && arrivals.isEmpty(); }
    public int time(long gameTime) { return (int) (gameTime - startedAt); }

    public void observeRelease(Level level, GasCloud source) {
        long tick = source.releasedAt();
        float charge = (float) source.charge();
        long amount = source.releasedAmount();
        if (tick == Long.MIN_VALUE || amount <= observedReleaseAmount) return;
        remainingPoolAmount += amount - observedReleaseAmount;
        observedReleaseAmount = amount;
        if (lastRelease != Long.MIN_VALUE && tick - lastRelease < 60 && charge < lastCharge + .08f) return;
        lastRelease = tick;
        lastCharge = charge;
        long now = level.getGameTime();
        release(level, source.volume(), time(now), charge, Math.max(0, now - tick));
    }

    public float concentration(BlockPos pos, double height, long gameTime) {
        var cell = cells.get(pos);
        if (cell == null || height < cell.floor / 16.0) return 0;
        float opacity = cell.opacity(time(gameTime));
        if (cell.inFreeFall()) opacity *= .42f;
        float ceiling = cell.ceiling(opacity) / 16f;
        if (height >= ceiling) return 0;
        return opacity * fade((ceiling - (float) height) * 5);
    }

    private void release(Level level, CloudVolume volume, int time, float charge, long elapsed) {
        var source = origin;
        if (!volume.contains(source)) {
            source = null;
            for (var side : Direction.values()) {
                var neighbor = origin.relative(side);
                if (volume.contains(neighbor)) {
                    source = neighbor;
                    if (side == Direction.UP) break;
                }
            }
        }
        if (source != null) {
            int dilution = dilutionTicks(level, source);
            offer(source, null, time, Mth.sqrt(charge) - elapsed / (float) dilution, dilution);
        }
    }

    private static int dilutionTicks(Level level, BlockPos pos) {
        int roof = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        return pos.getY() >= roof ? OPEN_AIR_TICKS : SHELTERED_TICKS;
    }

    public int spread(Level level, int time, int budget) {
        int work = 0;
        int cleanup = (budget + 1) / 2;
        while (work < cleanup && !expiries.isEmpty() && expiries.peek().tick() <= time) {
            var expiry = expiries.remove();
            if (expiry.tick() == expiry.cell().expiresAt) remove(expiry.cell());
            work++;
        }
        for (; work < budget && !arrivals.isEmpty() && arrivals.peek().tick() <= time; work++) {
            var next = arrivals.remove();
            if (!leading.remove(next.pos(), next)) continue;
            var direction = next.direction();
            var from = direction == null ? next.pos() : next.pos().relative(direction.getOpposite());
            float density = next.density() - (time - next.tick()) / (float) next.dilutionTicks();
            if (!level.hasChunkAt(from) || !level.hasChunkAt(next.pos())) {
                offer(next.pos(), direction, time + 20, density - 20f / next.dilutionTicks(), next.dilutionTicks());
                continue;
            }
            if (direction != null && !CloudVolume.canFlow(level, from, direction)) continue;
            arrive(level, next.pos(), direction, time, density);
        }
        for (; work < budget && !rises.isEmpty(); work++) {
            var next = rises.peek();
            var cell = next.cell();
            boolean active = cells.get(cell.pos) == cell && cell.remaining(time) >= .28f;
            if (active && (next.tick() > time || remainingPoolAmount < POOL_LAYER_CELL_MB)) break;
            rises.remove();
            cell.riseQueued = false;
            if (active) fillNextLayer(level, cell, time);
        }
        return work;
    }

    private void arrive(Level level, BlockPos pos, Direction direction, int time, float density) {
        if (density <= MIN_CONCENTRATION) return;
        var cell = cells.get(pos);
        if (cell != null && !improves(cell, direction, time, density)) return;
        if (cell == null) cell = add(level, pos);
        cell.previousDensity = cell.opacity(time);
        cell.density = Math.max(density, cell.remaining(time));
        cell.arrivedAt = time;
        cell.dilutionTicks = dilutionTicks(level, pos);
        cell.pooled |= !cell.canFall || direction == Direction.UP;
        cell.expiresAt = time + Mth.ceil((cell.density - MIN_CONCENTRATION) * cell.dilutionTicks);
        expiries.add(new Expiry(cell, cell.expiresAt));
        extend(level, cell);
    }

    private static boolean improves(Cell cell, Direction direction, int time, float density) {
        return direction == Direction.UP && !cell.pooled || density >= cell.remaining(time) + .06f;
    }

    private void remove(Cell cell) {
        if (!cells.remove(cell.pos, cell)) return;
        long key = SectionPos.asLong(cell.pos);
        var section = sections.get(key);
        section.cells.remove(cell.pos);
        if (section.cells.isEmpty()) {
            sections.remove(key);
            sectionClosed.accept(key);
        }
        for (int i = 0; i < SIDES.length; i++) {
            var neighbor = cells.get(cell.pos.relative(SIDES[i]));
            if (neighbor != null) neighbor.openings &= ~(1 << (i ^ 1));
        }
    }

    private Cell add(Level level, BlockPos pos) {
        var cell = new Cell(level, pos);
        cells.put(pos, cell);
        var section = sections.computeIfAbsent(SectionPos.asLong(pos), key -> {
            sectionOpened.accept(key);
            return new Section(pos);
        });
        section.cells.put(pos, cell);
        for (int i = 0; i < SIDES.length; i++) {
            var side = SIDES[i];
            var neighbor = cells.get(pos.relative(side));
            if (neighbor == null || !CloudVolume.canFlow(level, pos, side)) continue;
            cell.openings |= 1 << i;
            neighbor.openings |= 1 << (i ^ 1);
        }
        return cell;
    }

    private void extend(Level level, Cell cell) {
        int walls = 0;
        if (cell.pooled) {
            for (int i = 0; i < 4; i++) {
                var side = SIDES[i];
                if (queueStep(level, cell, side, 6)) continue;
                walls++;
            }
        }
        if (cell.canFall) queueStep(level, cell, Direction.DOWN, 4);
        if (walls >= 2 && !cell.riseQueued && cell.density >= .28f && CloudVolume.canFlow(level, cell.pos, Direction.UP)
            && !enclosedLayer(level, cell.pos).isEmpty()) {
            queueRise(cell, cell.arrivedAt + POOL_FILL_TICKS);
        }
    }

    private void queueRise(Cell cell, int tick) {
        cell.riseQueued = true;
        rises.add(new Rise(cell, tick));
    }

    private void fillNextLayer(Level level, Cell cell, int time) {
        var footprint = enclosedLayer(level, cell.pos);
        if (footprint.isEmpty()) return;
        var openings = new ArrayList<Cell>();
        for (var pos : footprint) {
            var current = cells.get(pos);
            if (current == null || !current.pooled || current.remaining(time) < .28f) {
                queueRise(cell, time + POOL_FILL_TICKS);
                return;
            }
            var next = pos.above();
            var above = cells.get(next);
            var pending = leading.get(next);
            if ((above == null || !above.pooled) && (pending == null || pending.direction() != Direction.UP)
                && CloudVolume.canFlow(level, pos, Direction.UP)) openings.add(current);
        }
        long cost = (long) openings.size() * POOL_LAYER_CELL_MB;
        if (remainingPoolAmount < cost) {
            queueRise(cell, time + POOL_FILL_TICKS);
            return;
        }
        remainingPoolAmount -= cost;
        for (var opening : openings)
            offer(opening.pos.above(), Direction.UP, time + 1, opening.remaining(time), opening.dilutionTicks);
    }

    private boolean queueStep(Level level, Cell cell, Direction side, int travel) {
        var next = cell.pos.relative(side);
        if (level.isOutsideBuildHeight(next)) return false;
        if (level.hasChunkAt(next) && !CloudVolume.canFlow(level, cell.pos, side)) return false;
        offer(next, side, cell.arrivedAt + travel, cell.density - travel / (float) cell.dilutionTicks, cell.dilutionTicks);
        return true;
    }

    private void offer(BlockPos pos, Direction direction, int tick, float density, int dilutionTicks) {
        if (density <= MIN_CONCENTRATION) return;
        var cell = cells.get(pos);
        if (cell != null && !improves(cell, direction, tick, density)) return;
        var old = leading.get(pos);
        if (old != null && (old.direction() == Direction.UP
            || direction != Direction.UP && (old.tick() < tick || old.tick() == tick && old.density() >= density))) return;
        var arrival = new Arrival(pos, direction, tick, density, dilutionTicks);
        leading.put(pos, arrival);
        arrivals.add(arrival);
    }

    private static Set<BlockPos> enclosedLayer(Level level, BlockPos origin) {
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            var pos = queue.remove();
            for (int i = 0; i < 4; i++) {
                var side = SIDES[i];
                var next = pos.relative(side);
                if (!level.hasChunkAt(next)) return Set.of();
                if (seen.contains(next) || !CloudVolume.canFlow(level, pos, side)) continue;
                if (seen.size() == POOL_AREA_LIMIT) return Set.of();
                seen.add(next);
                queue.add(next);
            }
        }
        return seen;
    }

    private static float fade(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
