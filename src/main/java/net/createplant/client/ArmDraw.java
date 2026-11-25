package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Tiny shape helper for the assembly overlays: a four sided bar between two points, sized width by height. */
final class ArmDraw {
    private ArmDraw() {}

    static void bar(VertexConsumer q, PoseStack.Pose pose, Vec3 a, Vec3 b, double width, double height, int rgb, int alpha, int light) {
        Vec3 d = b.subtract(a);
        Vec3 dir = d.lengthSqr() < 1e-10 ? new Vec3(1, 0, 0) : d.normalize();
        Vec3 ref = Math.abs(dir.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(ref).normalize().scale(width / 2), v = dir.cross(u).normalize().scale(height / 2);
        Vec3[] s = {u.add(v), u.reverse().add(v), u.reverse().add(v.reverse()), u.add(v.reverse())};
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255;
        for (int k = 0; k < 4; k++) {
            Vec3 s0 = s[k], s1 = s[(k + 1) % 4];
            vertex(q, pose, a.add(s0), r, g, bl, alpha, light);
            vertex(q, pose, a.add(s1), r, g, bl, alpha, light);
            vertex(q, pose, b.add(s1), r, g, bl, alpha, light);
            vertex(q, pose, b.add(s0), r, g, bl, alpha, light);
        }
    }

    /** A small glowing cube centred on {@code c}. */
    static void blob(VertexConsumer q, PoseStack.Pose pose, Vec3 c, double size, int rgb, int alpha, int light) {
        bar(q, pose, c.add(-size / 2, 0, 0), c.add(size / 2, 0, 0), size, size, rgb, alpha, light);
    }

    private static void vertex(VertexConsumer q, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a, int light) {
        q.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
