package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

record GasBillboard(Vector3f right, Vector3f up) {
    static GasBillboard facing(PoseStack.Pose pose, Quaternionf camera, boolean preview) {
        if (preview) {
            var view = pose.pose();
            return new GasBillboard(new Vector3f(view.m00(), view.m10(), view.m20()).normalize(),
                new Vector3f(view.m01(), view.m11(), view.m21()).normalize());
        }
        return new GasBillboard(new Vector3f(1, 0, 0).rotate(camera), new Vector3f(0, 1, 0).rotate(camera));
    }

    void draw(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float width, float height, float roll, int color, int alpha) {
        float cos = .7071f * Mth.cos(roll), sin = .7071f * Mth.sin(roll);
        float ax = (right.x * cos + up.x * sin) * width, ay = (right.y * cos + up.y * sin) * height, az = (right.z * cos + up.z * sin) * width;
        float bx = (up.x * cos - right.x * sin) * width, by = (up.y * cos - right.y * sin) * height, bz = (up.z * cos - right.z * sin) * width;
        GasCloudRenderer.vertex(buffer, pose, x - ax - bx, y - ay - by, z - az - bz, 0, 1, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x + ax - bx, y + ay - by, z + az - bz, 1, 1, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x + ax + bx, y + ay + by, z + az + bz, 1, 0, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x - ax + bx, y - ay + by, z - az + bz, 0, 0, color, alpha);
    }
}
