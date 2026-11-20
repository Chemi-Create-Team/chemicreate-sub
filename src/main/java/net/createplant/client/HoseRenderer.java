package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.CreatePlant;
import net.createplant.transport.HoseSocket;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class HoseRenderer {
    private static final RenderType HOSE = RenderType.entityCutoutNoCull(CreatePlant.location("textures/block/rubber_hose_mesh.png"));
    private static final int SIDES = 8;

    private HoseRenderer() { }

    static AABB bounds(HoseSocket.Endpoint endpoint) {
        var socket = endpoint.hose();
        var local = new AABB(socket.position());
        return !socket.connected() ? local : local.minmax(new AABB(socket.remote())).expandTowards(0, -3, 0).inflate(.25);
    }

    static <T extends BlockEntity & HoseSocket.Endpoint> void draw(T endpoint, PoseStack pose, MultiBufferSource buffers, int light) {
        var socket = endpoint.hose();
        if (!socket.connected() || endpoint.hoseFace() == null) return;
        if (socket.peer() != null && socket.position().compareTo(socket.remote()) > 0) return;
        var origin = Vec3.atLowerCornerOf(socket.position());
        var a = HoseSocket.mouth(socket.position(), endpoint.hoseFace()).subtract(origin);
        var b = HoseSocket.mouth(socket.remote(), socket.remoteFace()).subtract(origin);
        var delta = b.subtract(a);
        double horizontal = delta.horizontalDistance();
        double curvature = Math.max(1, horizontal * 1.65);
        double half = horizontal / (2*curvature);
        int segments = Math.max(5, (int) Math.ceil(delta.length() * 2));
        var level = endpoint.getLevel();
        var localMouth = socket.position().relative(endpoint.hoseFace());
        var remoteMouth = socket.remote().relative(socket.remoteFace());
        int startLight = level != null && level.hasChunkAt(localMouth) ? LevelRenderer.getLightColor(level, localMouth) : light;
        int endLight = level != null && level.hasChunkAt(remoteMouth) ? LevelRenderer.getLightColor(level, remoteMouth) : startLight;
        var buffer = buffers.getBuffer(HOSE);
        Vec3[] previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double height = curvature * (Math.cosh((2*t-1)*half) - Math.cosh(half));
            Vec3 center = a.lerp(b, t).add(0, height, 0);
            Vec3 tangent = delta.add(0, horizontal*Math.sinh((2*t-1)*half), 0).normalize();
            Vec3 side = tangent.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
            Vec3 up = side.cross(tangent).normalize();
            var next = new Vec3[SIDES];
            for (int j = 0; j < SIDES; j++) {
                double angle = Math.PI * 2 * j / SIDES;
                next[j] = center.add(up.scale(.076*Math.cos(angle))).add(side.scale(.076*Math.sin(angle)));
            }
            if (previous != null) {
                float lightMix = (float) ((i - .5) / segments);
                int segmentLight = LightTexture.pack(
                    Math.round(Mth.lerp(lightMix, LightTexture.block(startLight), LightTexture.block(endLight))),
                    Math.round(Mth.lerp(lightMix, LightTexture.sky(startLight), LightTexture.sky(endLight))));
                for (int j = 0; j < SIDES; j++) {
                    int k = (j + 1) % SIDES;
                    var normal = next[j].add(next[k]).scale(.5).subtract(center).normalize();
                    boolean stripe = j == 0;
                    int color = stripe ? 0xFFBB933C : 0xFF262A2A;
                    vertex(buffer, pose.last(), previous[j], normal, color, 0, 0, segmentLight);
                    vertex(buffer, pose.last(), previous[k], normal, color, 1, 0, segmentLight);
                    vertex(buffer, pose.last(), next[k], normal, color, 1, 1, segmentLight);
                    vertex(buffer, pose.last(), next[j], normal, color, 0, 1, segmentLight);
                }
            }
            previous = next;
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos, Vec3 normal, int color, float u, float v, int light) {
        buffer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z).setColor(color).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
