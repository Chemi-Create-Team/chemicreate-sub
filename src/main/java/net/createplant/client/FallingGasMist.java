package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.createplant.cloud.CloudVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

final class FallingGasMist {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int MAX_PATHS = 40, MAX_STEPS = 24, PUFFS = 4;
    private final List<Path> paths;

    private static final class Cell {
        final BlockPos pos;
        final Cell[] neighbors = new Cell[HORIZONTAL.length];
        Cell below;
        int distanceToDrop = Integer.MAX_VALUE;
        boolean room;

        Cell(BlockPos pos) { this.pos = pos; }
    }

    private record Point(int x, int y, int z, boolean room) { }
    private record Path(List<Point> points, float phase, int delay) { }

    FallingGasMist(Level level, CloudVolume volume) {
        var cells = new HashMap<BlockPos, Cell>();
        for (var pos : volume.positions()) cells.put(pos, new Cell(pos));
        var descending = new ArrayDeque<Cell>();
        var origin = volume.origin();
        for (var cell : cells.values()) {
            for (int side = 0; side < HORIZONTAL.length; side++) {
                var direction = HORIZONTAL[side];
                if (volume.flowsThrough(level, cell.pos, direction)) cell.neighbors[side] = cells.get(cell.pos.relative(direction));
            }
            cell.room = hasRoom(volume, level, cell);
            if (volume.flowsThrough(level, cell.pos, Direction.DOWN)) {
                cell.below = cells.get(cell.pos.below());
                cell.distanceToDrop = 0;
                descending.add(cell);
            }
        }
        while (!descending.isEmpty()) {
            var cell = descending.remove();
            for (var neighbor : cell.neighbors) {
                if (neighbor == null || neighbor.distanceToDrop <= cell.distanceToDrop + 1) continue;
                neighbor.distanceToDrop = cell.distanceToDrop + 1;
                descending.add(neighbor);
            }
        }

        var selected = new ArrayList<Path>();
        var starts = new ArrayList<>(cells.values());
        starts.sort(Comparator.comparingLong(cell -> HashCommon.murmurHash3(cell.pos.asLong())));
        var source = cells.get(origin);
        if (source != null) selected.add(path(source, origin));
        for (var cell : starts) {
            if (cell != source) selected.add(path(cell, origin));
            if (selected.size() == MAX_PATHS) break;
        }
        paths = List.copyOf(selected);
    }

    private static boolean hasRoom(CloudVolume volume, Level level, Cell cell) {
        for (int side = 0; side < HORIZONTAL.length; side++) {
            var neighbor = cell.neighbors[side];
            if (neighbor == null || !volume.flowsThrough(level, neighbor.pos, HORIZONTAL[(side + 1) % HORIZONTAL.length])) return false;
        }
        return true;
    }

    private static Path path(Cell start, BlockPos origin) {
        var points = new ArrayList<Point>();
        var visited = new HashSet<Cell>();
        long seed = HashCommon.murmurHash3(start.pos.asLong());
        var cell = start;
        int groundSteps = 0;
        for (int step = 0; cell != null && step <= MAX_STEPS; step++) {
            visited.add(cell);
            points.add(new Point(cell.pos.getX() - origin.getX(), cell.pos.getY() - origin.getY(), cell.pos.getZ() - origin.getZ(), cell.room));
            if (cell.below != null) {
                cell = cell.below;
                continue;
            }
            boolean towardDrop = cell.distanceToDrop != Integer.MAX_VALUE;
            if (!towardDrop && groundSteps++ >= 5) break;
            int first = (int) (seed >>> (step % 8 * 8)) & 3;
            Cell next = null;
            for (int i = 0; i < HORIZONTAL.length; i++) {
                var candidate = cell.neighbors[(first + i) & 3];
                if (candidate != null && !visited.contains(candidate) && (!towardDrop || candidate.distanceToDrop < cell.distanceToDrop)) {
                    next = candidate;
                    break;
                }
            }
            cell = next;
        }
        return new Path(List.copyOf(points), (seed >>> 48) / 65536f, Math.max(0, (origin.getY() - start.pos.getY()) * 3));
    }

    void render(VertexConsumer buffer, PoseStack.Pose pose, GasBillboard billboard, float time, float charge, float distanceFade,
        int top, float topHeight, int color) {
        for (var path : paths) {
            int steps = path.points().size() - 1;
            for (int i = 0; i < PUFFS; i++) {
                float clock = (time - path.delay()) / (24 + steps * 5f) - (i + path.phase()) / PUFFS;
                if (clock < 0) continue;
                float age = clock - Mth.floor(clock);
                int opacity = (int) (154 * Mth.sqrt(charge) * distanceFade * Mth.clamp(age * 7, 0, 1) * (1 - age));
                if (opacity == 0) continue;
                float progress = age * steps;
                int segment = Mth.floor(progress);
                var from = path.points().get(segment);
                var to = path.points().get(Math.min(segment + 1, steps));
                float fraction = progress - segment;
                float phase = path.phase() * Mth.TWO_PI + i * 1.71f;
                float fromHeight = from.y() == top ? topHeight : 1;
                float toHeight = to.y() == top ? topHeight : 1;
                float height = Mth.lerp(fraction, fromHeight, toHeight);
                float lift = .37f + .055f * Mth.sin(phase + age * 5);
                float x = Mth.lerp(fraction, from.x(), to.x()) + .048f * Mth.sin(phase + age * 7);
                float z = Mth.lerp(fraction, from.z(), to.z()) + .048f * Mth.cos(phase + age * 7);
                float y = Mth.lerp(fraction, from.y(), to.y()) - .5f + lift * height;
                boolean descending = from.y() != to.y();
                float widthLimit = !descending && from.room() && to.room() ? .91f : .45f;
                float width = Math.min(widthLimit, .23f + .86f * age);
                float depth = height * Math.min(.31f, .15f + age * .24f);
                billboard.draw(buffer, pose, x, y, z, width, depth, .65f * Mth.sin(phase + age), color, opacity);
            }
        }
    }
}
