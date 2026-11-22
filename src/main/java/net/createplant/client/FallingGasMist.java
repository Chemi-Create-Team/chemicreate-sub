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
    private static final int DOWN = 16, SAMPLES = 6;
    private static final float AXIS_BLEND = .78f;
    private final GroundGasField field;
    private final BlockPos origin;
    private final int sampleX, sampleZ;

    FallingGasMist(GroundGasField field) {
        this.field = field;
        origin = field.origin();
        sampleX = Math.floorMod(origin.getX(), 256);
        sampleZ = Math.floorMod(origin.getZ(), 256);
    }

    void render(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        float time, float previewCharge, int color) {
        for (var section : field.sections()) {
            if (previewView == null && (section.bounds().distanceToSqr(camera) >= 48 * 48 || !frustum.isVisible(section.bounds()))) continue;
            for (var cell : section.cells()) {
                float opacity = previewView == null ? cell.opacity(time) : Mth.sqrt(previewCharge) * fade((time - cell.arrivedAt()) / 32);
                renderCell(buffer, pose, camera, frustum, previewView, color, cell, opacity);
            }
        }
    }

    private void renderCell(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Frustum frustum, Vector3fc previewView,
        int color, GroundGasField.Cell cell, float opacity) {
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
        float x = pos.getX() - origin.getX() - .5f;
        float y = pos.getY() - origin.getY() - .5f;
        float z = pos.getZ() - origin.getZ() - .5f;
        int ceiling = cap;
        float height = (ceiling - cell.floor()) / 16f;
        if (height <= 0) return;
        int data = cell.openings() & ~DOWN | (cell.inFreeFall() ? DOWN : 0) | cell.floor() << 6 | ceiling << 11;
        float bottom = y + cell.floor() / 16f;
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
