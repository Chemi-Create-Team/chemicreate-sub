package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import net.createmod.ponder.api.level.PonderLevel;
import net.createplant.cloud.CloudVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3fc;

final class FallingGasMist {
    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP
    };
    private final Map<BlockPos, Cell> cells = new HashMap<>();
    private final BlockPos origin;
    private final int top, sampleX, sampleZ;

    private static final class Cell {
        final BlockPos pos;
        final int openings, floor;
        float arrival = Float.POSITIVE_INFINITY;

        Cell(Level level, CloudVolume volume, BlockPos pos, BoundingBox previewBounds) {
            this.pos = pos;
            int openings = 0;
            for (int i = 0; i < SIDES.length; i++) {
                var side = SIDES[i];
                if ((previewBounds == null || previewBounds.isInside(pos.relative(side))) && volume.flowsThrough(level, pos, side))
                    openings |= 1 << i;
            }
            this.openings = openings;
            int floor = 0;
            var state = level.getBlockState(pos);
            if (!state.isAir() && (openings & 16) == 0) {
                for (var box : state.getCollisionShape(level, pos).toAabbs())
                    if (box.minY == 0 && box.minX == 0 && box.maxX == 1 && box.minZ == 0 && box.maxZ == 1)
                        floor = Math.max(floor, Mth.floor(box.maxY * 16));
            }
            this.floor = floor;
        }
    }

    private record Arrival(Cell cell, float time) { }

    FallingGasMist(Level level, CloudVolume volume, FallingGasMist previous, float time) {
        origin = volume.origin();
        top = (int) volume.bounds().maxY - 1 - origin.getY();
        sampleX = Math.floorMod(origin.getX(), 256);
        sampleZ = Math.floorMod(origin.getZ(), 256);
        var pending = new PriorityQueue<Arrival>(Comparator.comparingDouble(Arrival::time));
        boolean hasOrigin = volume.contains(origin);
        float releaseTime = previous == null ? 0 : time;
        var previewBounds = level instanceof PonderLevel preview ? preview.getBounds() : null;
        for (var pos : volume.positions()) {
            if (previewBounds != null && !previewBounds.isInside(pos)) continue;
            var cell = new Cell(level, volume, pos, previewBounds);
            cells.put(pos, cell);
            var oldCell = previous == null ? null : previous.cells.get(pos);
            if (oldCell != null) schedule(pending, cell, oldCell.arrival);
            else if (hasOrigin ? pos.equals(origin) : pos.distManhattan(origin) == 1) schedule(pending, cell, releaseTime);
        }
        while (!pending.isEmpty()) {
            var next = pending.remove();
            var cell = next.cell();
            if (next.time() != cell.arrival) continue;
            for (int i = 0; i < SIDES.length; i++) {
                if ((cell.openings & 1 << i) == 0) continue;
                var neighbor = cells.get(cell.pos.relative(SIDES[i]));
                float travel = switch (SIDES[i]) {
                    case DOWN -> 8;
                    case UP -> 23;
                    default -> 13;
                };
                float start = previous == null || previous.cells.containsKey(neighbor.pos) ? next.time() : Math.max(next.time(), time);
                schedule(pending, neighbor, start + travel);
            }
        }
    }

    private static void schedule(PriorityQueue<Arrival> pending, Cell cell, float time) {
        if (time >= cell.arrival) return;
        cell.arrival = time;
        pending.add(new Arrival(cell, time));
    }

    void render(VertexConsumer buffer, PoseStack.Pose pose, Vector3fc view, float time, float charge, float distanceFade,
        float topHeight, int color, int samples) {
        float xView = Math.abs(view.x()), yView = Math.abs(view.y()), zView = Math.abs(view.z());
        float threshold = Math.max(xView, Math.max(yView, zView)) * .78f;
        float xWeight = Math.max(0, xView - threshold);
        float yWeight = Math.max(0, yView - threshold);
        float zWeight = Math.max(0, zView - threshold);
        float visibility = Mth.sqrt(charge) * distanceFade / (xWeight + yWeight + zWeight);
        int cap = Math.max(1, Math.round(topHeight * 16));
        for (var cell : cells.values()) {
            float age = Mth.clamp((time - cell.arrival) / 32, 0, 1);
            float opacity = visibility * age * age * (3 - 2 * age);
            if (opacity < .004f) continue;
            float x = cell.pos.getX() - origin.getX() - .5f;
            float y = cell.pos.getY() - origin.getY() - .5f;
            float z = cell.pos.getZ() - origin.getZ() - .5f;
            int ceiling = cell.pos.getY() - origin.getY() == top ? cap : 16;
            float height = (ceiling - cell.floor) / 16f;
            if (height <= 0) continue;
            // The shader decodes the six face flags followed by the floor and ceiling in sixteenths of a block.
            int data = cell.openings | cell.floor << 6 | ceiling << 11;
            float bottom = y + cell.floor / 16f;
            float upper = bottom + height;
            // Blend adjacent slicing axes so rotating the camera does not switch the sampling abruptly.
            int xAlpha = sliceOpacity(opacity, xWeight, xView, 1, samples);
            int yAlpha = sliceOpacity(opacity, yWeight, yView, height, samples);
            int zAlpha = sliceOpacity(opacity, zWeight, zView, 1, samples);
            for (int i = 0; i < samples; i++) {
                float t = (i + .5f) / samples;
                if (xAlpha > 0) {
                    float u = x + t;
                    vertex(buffer, pose, u, bottom, z, data, color, xAlpha);
                    vertex(buffer, pose, u, bottom, z + 1, data, color, xAlpha);
                    vertex(buffer, pose, u, upper, z + 1, data, color, xAlpha);
                    vertex(buffer, pose, u, upper, z, data, color, xAlpha);
                }
                if (yAlpha > 0) {
                    float v = bottom + t * height;
                    vertex(buffer, pose, x, v, z, data, color, yAlpha);
                    vertex(buffer, pose, x + 1, v, z, data, color, yAlpha);
                    vertex(buffer, pose, x + 1, v, z + 1, data, color, yAlpha);
                    vertex(buffer, pose, x, v, z + 1, data, color, yAlpha);
                }
                if (zAlpha > 0) {
                    float w = z + t;
                    vertex(buffer, pose, x, bottom, w, data, color, zAlpha);
                    vertex(buffer, pose, x + 1, bottom, w, data, color, zAlpha);
                    vertex(buffer, pose, x + 1, upper, w, data, color, zAlpha);
                    vertex(buffer, pose, x, upper, w, data, color, zAlpha);
                }
            }
        }
    }

    private static int sliceOpacity(float opacity, float weight, float view, float length, int samples) {
        return weight == 0 ? 0 : Mth.clamp(Math.round(255 * opacity * weight * length / (view * samples)), 0, 255);
    }

    private void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int data, int color, int opacity) {
        buffer.addVertex(pose, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, opacity)
            .setUv(x + sampleX, z + sampleZ).setUv2(Math.round(y * 1024), data);
    }
}
