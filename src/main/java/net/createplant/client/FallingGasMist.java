package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import net.createmod.ponder.api.level.PonderLevel;
import net.createplant.cloud.CloudVolume;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

final class FallingGasMist {
    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP
    };
    private static final int DOWN = 16, UP = 32, SAMPLES = 6, SPREAD_PER_TICK = 160;
    private static final float MIN_CONCENTRATION = .026f, AXIS_BLEND = .78f, FADE_TICKS = 24;
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final Map<BlockPos, Cell> receding = new HashMap<>();
    private final Map<BlockPos, Spread> leading = new HashMap<>();
    private final PriorityQueue<Spread> pending = new PriorityQueue<>(Comparator.comparingDouble(Spread::concentration).reversed()
        .thenComparingDouble(Spread::arrival).thenComparingInt(next -> next.pos().getY()));
    private Map<BlockPos, Cell> previousCells = Map.of();
    private final BlockPos origin;
    private AABB bounds;
    private final int top, sampleX, sampleZ;
    private final float changedAt;
    private int spreadTick = Integer.MIN_VALUE;

    private static final class Cell {
        final BlockPos pos;
        final AABB bounds;
        final float concentration;
        final int floor;
        final boolean canFall;
        int openings;
        float previousConcentration;
        float arrival = Float.POSITIVE_INFINITY;
        float recedesAt = Float.POSITIVE_INFINITY;

        Cell(Level level, BlockPos pos, float concentration) {
            this.pos = pos;
            bounds = new AABB(pos);
            previousConcentration = this.concentration = concentration;
            canFall = CloudVolume.canFlow(level, pos, Direction.DOWN);
            int floor = 0;
            var state = level.getBlockState(pos);
            if (!state.isAir()) {
                for (var box : state.getCollisionShape(level, pos).toAabbs())
                    if (box.minY == 0 && box.minX == 0 && box.maxX == 1 && box.minZ == 0 && box.maxZ == 1)
                        floor = Math.max(floor, Mth.floor(box.maxY * 16));
            }
            this.floor = floor;
        }
    }

    private record Spread(BlockPos pos, Direction direction, float concentration, float arrival) { }
    private record Arrival(Cell cell, float time) { }

    FallingGasMist(Level level, CloudVolume volume, FallingGasMist previous, float time) {
        changedAt = time;
        origin = volume.origin();
        bounds = volume.bounds();
        top = (int) bounds.maxY - 1;
        sampleX = Math.floorMod(origin.getX(), 256);
        sampleZ = Math.floorMod(origin.getZ(), 256);
        var previewBounds = level instanceof PonderLevel preview ? preview.getBounds() : null;
        for (var pos : volume.positions())
            if (previewBounds == null || previewBounds.isInside(pos)) cells.put(pos, new Cell(level, pos, 1));
        connect(level, previous, time);
        if (previous != null) {
            for (var cell : previous.cells.values()) {
                if (cells.containsKey(cell.pos) || cell.arrival >= time) continue;
                cell.previousConcentration = previous.concentration(cell, time);
                cell.recedesAt = time;
                receding.put(cell.pos, cell);
                bounds = bounds.minmax(cell.bounds);
            }
        }
        if (!(level instanceof PonderLevel)) {
            if (previous != null) previousCells = previous.cells;
            for (var cell : cells.values()) extend(level, cell);
        }
    }

    void spread(Level level, float time) {
        int tick = Mth.floor(time);
        if (tick == spreadTick) return;
        spreadTick = tick;
        if (time - changedAt >= FADE_TICKS) receding.clear();
        for (int work = 0; work < SPREAD_PER_TICK && !pending.isEmpty(); work++) {
            var next = pending.remove();
            if (!leading.remove(next.pos(), next)) continue;
            if (!CloudVolume.canFlow(level, next.pos().relative(next.direction().getOpposite()), next.direction())) continue;
            var cell = new Cell(level, next.pos(), next.concentration());
            cell.arrival = next.arrival();
            var old = previousCells.get(cell.pos);
            if (old != null) {
                cell.arrival = Math.min(cell.arrival, old.arrival);
                cell.previousConcentration = receding.remove(cell.pos) == null ? old.concentration : old.previousConcentration;
            }
            cells.put(next.pos(), cell);
            bounds = bounds.minmax(cell.bounds);
            for (int i = 0; i < SIDES.length; i++) {
                var side = SIDES[i];
                var neighbor = cells.get(cell.pos.relative(side));
                if (neighbor == null || !CloudVolume.canFlow(level, cell.pos, side)) continue;
                cell.openings |= 1 << i;
                neighbor.openings |= 1 << (i ^ 1);
            }
            extend(level, cell);
        }
        if (pending.isEmpty()) previousCells = Map.of();
    }

    private void extend(Level level, Cell cell) {
        for (int i = cell.canFall ? 4 : 0; i < 5; i++) {
            var side = SIDES[i];
            var next = cell.pos.relative(side);
            float remaining = cell.concentration * (side == Direction.DOWN ? .985f : .82f);
            if (remaining <= MIN_CONCENTRATION || cells.containsKey(next)) continue;
            var old = leading.get(next);
            float arrival = Math.max(changedAt, cell.arrival) + travelTime(side);
            if (old != null && (old.concentration() > remaining || old.concentration() == remaining && old.arrival() <= arrival)) continue;
            if (side != Direction.DOWN && !CloudVolume.canFlow(level, cell.pos, side)) continue;
            var candidate = new Spread(next, side, remaining, arrival);
            leading.put(next, candidate);
            pending.add(candidate);
        }
    }

    private void connect(Level level, FallingGasMist previous, float time) {
        var pending = new PriorityQueue<Arrival>(Comparator.comparingDouble(Arrival::time));
        for (var cell : cells.values()) {
            for (int i = 0; i < SIDES.length; i++) {
                var side = SIDES[i];
                if (cells.containsKey(cell.pos.relative(side)) && CloudVolume.canFlow(level, cell.pos, side))
                    cell.openings |= 1 << i;
            }
            var old = previous == null ? null : previous.cells.get(cell.pos);
            if (old != null) {
                cell.previousConcentration = previous.concentration(old, time);
                schedule(pending, cell, old.arrival);
            }
            else if (cell.pos.equals(origin) || !cells.containsKey(origin) && cell.pos.distManhattan(origin) == 1)
                schedule(pending, cell, previous == null ? 0 : time);
        }
        advance(pending, previous, time);
        // A packet can arrive before the adjacent chunk or door state. The synced core remains a valid source.
        for (var cell : cells.values()) {
            if (cell.concentration < 1 || Float.isFinite(cell.arrival)) continue;
            schedule(pending, cell, (previous == null ? 0 : time) + cell.pos.distManhattan(origin) * 10);
        }
        advance(pending, previous, time);
    }

    private void advance(PriorityQueue<Arrival> pending, FallingGasMist previous, float time) {
        while (!pending.isEmpty()) {
            var next = pending.remove();
            var cell = next.cell();
            if (next.time() != cell.arrival) continue;
            for (int i = 0; i < SIDES.length; i++) {
                if ((cell.openings & 1 << i) == 0) continue;
                var neighbor = cells.get(cell.pos.relative(SIDES[i]));
                float start = previous == null || previous.cells.containsKey(neighbor.pos) ? next.time() : Math.max(next.time(), time);
                schedule(pending, neighbor, start + travelTime(SIDES[i]));
            }
        }
    }

    private static int travelTime(Direction side) {
        return switch (side) {
            case DOWN -> 5;
            case UP -> 23;
            default -> 10;
        };
    }

    private static void schedule(PriorityQueue<Arrival> pending, Cell cell, float time) {
        if (time >= cell.arrival) return;
        cell.arrival = time;
        pending.add(new Arrival(cell, time));
    }

    AABB bounds() { return bounds; }

    private float concentration(Cell cell, float time) {
        return Mth.lerp(fade((time - changedAt) / FADE_TICKS), cell.previousConcentration, cell.concentration);
    }

    void render(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        float time, float charge, int color) {
        int cap = Math.max(1, Math.round((.25f + .75f * charge) * 16));
        float visibility = Mth.sqrt(charge);
        for (var cell : cells.values())
            renderCell(buffer, pose, camera, frustum, previewView, time, cap, color, visibility, cell);
        for (var cell : receding.values())
            renderCell(buffer, pose, camera, frustum, previewView, time, cap, color, visibility, cell);
    }

    private void renderCell(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        float time, int cap, int color, float visibility, Cell cell) {
        float age = fade((time - cell.arrival) / 32);
        float departure = fade((cell.recedesAt + FADE_TICKS - time) / FADE_TICKS);
        float density = Float.isFinite(cell.recedesAt) ? cell.previousConcentration : concentration(cell, time);
        float concentration = (density - MIN_CONCENTRATION) / (1 - MIN_CONCENTRATION);
        float opacity = visibility * age * departure * concentration;
        if (cell.canFall) opacity *= Mth.lerp(fade((time - cell.arrival - 12) / 38), .65f, .30f);
        if (opacity < .004f) return;
        var pos = cell.pos;
        boolean preview = previewView != null;
        float vx, vy, vz;
        if (preview) {
            vx = Math.abs(previewView.x());
            vy = Math.abs(previewView.y());
            vz = Math.abs(previewView.z());
        } else {
            vx = (float) Math.abs(pos.getX() + .5 - camera.x);
            vy = (float) Math.abs(pos.getY() + .5 - camera.y);
            vz = (float) Math.abs(pos.getZ() + .5 - camera.z);
            float distance = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            opacity *= fade((48 - distance) / 16);
            if (opacity < .004f || !frustum.isVisible(cell.bounds)) return;
        }
        float x = pos.getX() - origin.getX() - .5f;
        float y = pos.getY() - origin.getY() - .5f;
        float z = pos.getZ() - origin.getZ() - .5f;
        int ceiling = pos.getY() == top && (cell.openings & UP) == 0 ? cap : 16;
        float height = (ceiling - cell.floor) / 16f;
        if (height <= 0) return;
        int data = cell.openings | (cell.canFall ? DOWN : 0) | cell.floor << 6 | ceiling << 11;
        float bottom = y + cell.floor / 16f;
        float upper = bottom + height;
        float threshold = Math.max(vx, Math.max(vy, vz)) * AXIS_BLEND;
        float xWeight = Math.max(0, vx - threshold);
        float yWeight = Math.max(0, vy - threshold);
        float zWeight = Math.max(0, vz - threshold);
        float step = opacity / SAMPLES;
        if (preview) step /= xWeight + yWeight + zWeight;
        float margin = .5f * (1 + AXIS_BLEND);
        int xAlpha = sliceDepth(step, 1, preview, xWeight, vx, margin, threshold);
        int yAlpha = sliceDepth(step, height, preview, yWeight, vy, margin, threshold);
        int zAlpha = sliceDepth(step, 1, preview, zWeight, vz, margin, threshold);
        int yOffset = Math.floorMod(pos.getY() + 511, 1022) - 511;
        for (int i = 0; i < SAMPLES; i++) {
            float t = (i + .5f) / SAMPLES;
            if (xAlpha > 0) {
                float u = x + t;
                int axis = preview ? -1 : 0;
                vertex(buffer, pose, u, bottom, z, bottom - y, yOffset, data, color, axis, xAlpha);
                vertex(buffer, pose, u, bottom, z + 1, bottom - y, yOffset, data, color, axis, xAlpha);
                vertex(buffer, pose, u, upper, z + 1, upper - y, yOffset, data, color, axis, xAlpha);
                vertex(buffer, pose, u, upper, z, upper - y, yOffset, data, color, axis, xAlpha);
            }
            if (yAlpha > 0) {
                float v = bottom + t * height;
                int axis = preview ? -1 : 1;
                vertex(buffer, pose, x, v, z, v - y, yOffset, data, color, axis, yAlpha);
                vertex(buffer, pose, x + 1, v, z, v - y, yOffset, data, color, axis, yAlpha);
                vertex(buffer, pose, x + 1, v, z + 1, v - y, yOffset, data, color, axis, yAlpha);
                vertex(buffer, pose, x, v, z + 1, v - y, yOffset, data, color, axis, yAlpha);
            }
            if (zAlpha > 0) {
                float w = z + t;
                int axis = preview ? -1 : 2;
                vertex(buffer, pose, x, bottom, w, bottom - y, yOffset, data, color, axis, zAlpha);
                vertex(buffer, pose, x + 1, bottom, w, bottom - y, yOffset, data, color, axis, zAlpha);
                vertex(buffer, pose, x + 1, upper, w, upper - y, yOffset, data, color, axis, zAlpha);
                vertex(buffer, pose, x, upper, w, upper - y, yOffset, data, color, axis, zAlpha);
            }
        }
    }

    private static float fade(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int sliceDepth(float step, float length, boolean preview, float weight, float component, float margin, float threshold) {
        if (preview) return weight == 0 ? 0 : Math.round(16384 * step * length * weight / component);
        return component + margin < threshold ? 0 : Math.round(16384 * step * length);
    }

    private void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float localY,
        int yOffset, int data, int color, int axis, int opticalDepth) {
        buffer.addVertex(pose, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, 255)
            .setUv(x + sampleX, z + sampleZ).setUv1(axis, opticalDepth)
            .setUv2(yOffset * 64 + Math.round((localY - .5f) * 64), data);
    }
}
