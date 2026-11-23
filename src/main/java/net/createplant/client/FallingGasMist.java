package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.cloud.GroundGasField;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

final class FallingGasMist {
    private static final int DOWN = 16, UP = 32, SAMPLES = 6;
    private static final float AXIS_BLEND = .78f;
    /** Width over which a closed side fades the mist out, in blocks. The old ground shader varied it between .28 and .66. */
    private static final float EDGE = .4f;
    /** The densest pattern value the texture's alpha is normalised to, and the mist's mean thickness without it. */
    private static final float PEAK = 1.92f, VEIL = .295f;
    /** Height above the floor at which the dense bottom layer has fallen to half, then the heights the mist is cut at to follow it. */
    private static final float FLOOR_FALL = 4.68f;
    private static final float[] FLOOR_STEPS = {0, .15f, .4f};
    private final GroundGasField field;
    private final BlockPos origin;
    private final int sampleX, sampleZ;

    FallingGasMist(GroundGasField field) {
        this.field = field;
        origin = field.origin();
        sampleX = Math.floorMod(origin.getX(), 256);
        sampleZ = Math.floorMod(origin.getZ(), 256);
    }

    /** {@code day} is the fraction of a game day, which drifts the pattern; the texture tile repeats, so the day boundary is seamless. */
    void render(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        float time, float previewCharge, int color, float day) {
        for (var section : field.sections()) {
            if (previewView == null && (section.bounds().distanceToSqr(camera) >= 48 * 48 || !frustum.isVisible(section.bounds()))) continue;
            for (var cell : section.cells()) {
                float opacity = previewView == null ? cell.opacity(time) : Mth.sqrt(previewCharge) * fade((time - cell.arrivedAt()) / 32);
                renderCell(buffer, pose, camera, frustum, previewView, color, cell, opacity, day);
            }
        }
    }

    private void renderCell(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        int color, GroundGasField.Cell cell, float opacity, float day) {
        if (cell.inFreeFall()) opacity *= .42f;
        if (opacity < GroundGasField.MIN_CONCENTRATION) return;
        int cap = cell.ceiling(opacity);
        var pos = cell.pos();
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
            if (opacity < .004f || !frustum.isVisible(cell.bounds())) return;
        }
        float height = (cap - cell.floor()) / 16f;
        if (height <= 0) return;
        var shape = new Shape(cell.openings() & ~DOWN | (cell.inFreeFall() ? DOWN : 0), cell.floor() / 16f, cap / 16f, pos.getX() - origin.getX() - .5f,
            pos.getY() - origin.getY() - .5f, pos.getZ() - origin.getZ() - .5f, pos.getY(), day, color);
        float threshold = Math.max(vx, Math.max(vy, vz)) * AXIS_BLEND;
        float xWeight = Math.max(0, vx - threshold);
        float yWeight = Math.max(0, vy - threshold);
        float zWeight = Math.max(0, vz - threshold);
        float step = opacity / SAMPLES;
        if (preview) step /= xWeight + yWeight + zWeight;
        float margin = .5f * (1 + AXIS_BLEND);
        float xDepth = sliceDepth(step, 1, preview, xWeight, vx, margin, threshold);
        float yDepth = sliceDepth(step, height, preview, yWeight, vy, margin, threshold);
        float zDepth = sliceDepth(step, 1, preview, zWeight, vz, margin, threshold);
        var along = edges(shape.open, 1, 2);
        var across = edges(shape.open, 4, 8);
        var upright = uprightSteps(shape);
        for (int i = 0; i < SAMPLES; i++) {
            float t = (i + .5f) / SAMPLES;
            if (xDepth > 0) slice(buffer, pose, shape, xDepth, 0, t, along, upright);
            if (yDepth > 0) slice(buffer, pose, shape, yDepth, 1, shape.floor + t * height, across, along);
            if (zDepth > 0) slice(buffer, pose, shape, zDepth, 2, t, across, upright);
        }
    }

    /** One cell's fixed numbers. {@code x}, {@code y}, {@code z} are the cell corner relative to the mist's origin; {@code floor} and {@code ceiling} are in blocks. */
    private record Shape(int open, float floor, float ceiling, float x, float y, float z, int worldY, float day, int color) { }

    /** Splits [0, 1] where a closed side begins to fade, so the fade is as steep as the shader's and not spread over the whole cell. */
    private static float[] edges(int open, int low, int high) {
        boolean cutLow = (open & low) == 0, cutHigh = (open & high) == 0;
        if (cutLow && cutHigh) return new float[] {0, EDGE, 1 - EDGE, 1};
        if (cutLow) return new float[] {0, EDGE, 1};
        if (cutHigh) return new float[] {0, 1 - EDGE, 1};
        return new float[] {0, 1};
    }

    /** Heights, in blocks from the cell bottom, at which an upright slice changes: the dense layer above the floor, and the fade under a closed ceiling. */
    private static float[] uprightSteps(Shape shape) {
        float[] steps = new float[FLOOR_STEPS.length + 3];
        int count = 0;
        float low = shape.floor, high = shape.ceiling;
        if ((shape.open & DOWN) == 0) {
            for (float step : FLOOR_STEPS) if (low + step < high) steps[count++] = low + step;
        } else steps[count++] = low;
        if ((shape.open & UP) == 0) {
            float top = high - Math.min(.55f, (high - low) * .85f);
            if (top > steps[count - 1]) steps[count++] = top;
        }
        steps[count++] = high;
        return java.util.Arrays.copyOf(steps, count);
    }

    private void slice(VertexConsumer buffer, PoseStack.Pose pose, Shape shape, float depth, int axis, float fixed, float[] first, float[] second) {
        for (int a = 0; a + 1 < first.length; a++)
            for (int b = 0; b + 1 < second.length; b++) {
                point(buffer, pose, shape, depth, axis, fixed, first[a], second[b]);
                point(buffer, pose, shape, depth, axis, fixed, first[a + 1], second[b]);
                point(buffer, pose, shape, depth, axis, fixed, first[a + 1], second[b + 1]);
                point(buffer, pose, shape, depth, axis, fixed, first[a], second[b + 1]);
            }
    }

    /**
     * Axis 0 is a plane of constant x spanning z and height, 1 a plane of constant height spanning x and z, 2 a plane of constant z spanning x and height.
     * The mist's thickness at the point is the old shader's closed form, with the pattern's own variation left to the texture.
     */
    private void point(VertexConsumer buffer, PoseStack.Pose pose, Shape shape, float depth, int axis, float fixed, float p, float q) {
        float lx = axis == 0 ? fixed : p, ly = axis == 1 ? fixed : q, lz = axis == 0 ? p : axis == 1 ? q : fixed;
        float lowY = shape.floor, edge = side(lz, 1, shape.open) * side(1 - lz, 2, shape.open) * side(lx, 4, shape.open) * side(1 - lx, 8, shape.open);
        if ((shape.open & UP) == 0) edge *= fade((shape.ceiling - ly) / Math.min(.55f, (shape.ceiling - lowY) * .85f));
        float thickness = (shape.open & DOWN) != 0 ? VEIL * .46f : VEIL + 1.15f * (float) Math.exp(-FLOOR_FALL * Math.max(0, ly - lowY));
        int alpha = Mth.clamp(Math.round(255 * (1 - (float) Math.exp(-edge * thickness * PEAK * depth))), 0, 255);
        float x = shape.x + lx, y = shape.y + ly, z = shape.z + lz;
        float wx = x + sampleX, wz = z + sampleZ, wy = shape.worldY + ly;
        float u, v;
        if (axis == 1) {
            u = wx / 32 - shape.day * 6;
            v = wz / 32 + wy * .37f - shape.day * 3;
        } else {
            u = (axis == 0 ? wz / 32 + wx * .41f : wx / 32 + wz * .41f) - shape.day * 6;
            v = wy * .0886f + (axis == 0 ? 0 : .5f) - shape.day * 3;
        }
        GasCloudRenderer.vertex(buffer, pose, x, y, z, u, v, shape.color, alpha);
    }

    private static float side(float distance, int bit, int open) {
        return (open & bit) != 0 ? 1 : fade(distance / EDGE);
    }

    private static float fade(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float sliceDepth(float step, float length, boolean preview, float weight, float component, float margin, float threshold) {
        if (preview) return weight == 0 ? 0 : step * length * weight / component;
        return component + margin < threshold ? 0 : step * length;
    }
}
