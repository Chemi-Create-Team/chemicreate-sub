package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createplant.CreatePlant;
import net.createplant.cloud.CloudVolume;
import net.createplant.cloud.GasCloud;
import net.createplant.cloud.GasClouds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class GasCloudRenderer extends EntityRenderer<GasCloud> {
    private static final ResourceLocation TEXTURE = CreatePlant.location("textures/entity/gas_mist.png");
    private static final RenderType LAYER = RenderType.entityTranslucent(TEXTURE);
    private static final Direction[] DIRECTIONS = Direction.values();
    private final WeakHashMap<GasCloud, Mesh> meshes = new WeakHashMap<>();

    private record Face(float x, float y, float z, float height, Direction side) { }
    private record Mesh(CloudVolume volume, float topHeight, List<Face> faces) { }

    public GasCloudRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(GasCloud cloud) { return TEXTURE; }

    @Override public void render(GasCloud cloud, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean preview = cloud.level() instanceof PonderLevel;
        double distance = preview ? 0 : cloud.distanceToSqr(entityRenderDispatcher.camera.getPosition());
        var style = cloud.style();
        if (style == null || style.color() == null) return;
        var alpha = (int) (100 * cloud.charge() * Mth.clamp((48 - Math.sqrt(distance)) / 16, 0, 1));
        if (alpha == 0) return;
        float topHeight = cloud.topHeight();
        var mesh = meshes.get(cloud);
        if (mesh == null || mesh.volume() != cloud.volume() || mesh.topHeight() != topHeight) {
            var faces = new ArrayList<Face>();
            for (var pos : cloud.volume().positions()) {
                var relative = pos.subtract(cloud.blockPosition());
                float height = pos.getY() == cloud.volume().bounds().maxY - 1 ? topHeight : 1;
                for (var side : DIRECTIONS)
                    if (!cloud.volume().contains(pos.relative(side)))
                        faces.add(new Face(relative.getX() - .5f, relative.getY() - .5f, relative.getZ() - .5f, height, side));
            }
            mesh = new Mesh(cloud.volume(), topHeight, List.copyOf(faces));
            meshes.put(cloud, mesh);
        }
        var buffer = buffers.getBuffer(LAYER);
        var matrix = pose.last();
        float motion = (cloud.tickCount % 160 + partialTick) / 160f;
        for (var face : mesh.faces()) draw(buffer, matrix, face, motion, style.color(), alpha);
        if (preview && buffers instanceof SuperRenderTypeBuffer ponderBuffers) ponderBuffers.draw(LAYER);
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose matrix, Face face, float motion, int color, int alpha) {
        var side = face.side();
        int sign = side.getAxisDirection().getStep();
        float x = face.x(), y = face.y(), z = face.z();
        float fixed = sign > 0 ? .996f : .004f;
        for (int i = 0; i < 4; i++) {
            int a = i == 1 || i == 2 ? 1 : 0;
            int b = i >= 2 ? 1 : 0;
            if (sign < 0) a = 1 - a;
            switch (side.getAxis()) {
                case X -> vertex(buffer, matrix, x + fixed, y + a * face.height(), z + b, (z + b) * .5f + motion, (y + a) * .5f, color, alpha, side);
                case Y -> vertex(buffer, matrix, x + a, y + fixed * face.height(), z + b, (z + b) * .5f + motion, (x + a) * .5f, color, alpha, side);
                case Z -> vertex(buffer, matrix, x + a, y + b * face.height(), z + fixed, (x + a) * .5f + motion, (y + b) * .5f, color, alpha, side);
            }
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float x, float y, float z, float u, float v, int color, int alpha, Direction side) {
        buffer.addVertex(matrix, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(matrix, side.getStepX(), side.getStepY(), side.getStepZ());
    }

    private static GasCloud strongest(Vec3 position) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var box = AABB.ofSize(position, .002, .002, .002);
        GasCloud strongest = null;
        for (var cloud : GasClouds.in(level, box)) {
            var style = cloud.style();
            if (style != null && style.color() != null && cloud.contains(position)
                && (strongest == null || strongest.charge() < cloud.charge())) strongest = cloud;
        }
        return strongest;
    }

    public static void fogColor(ViewportEvent.ComputeFogColor event) {
        var cloud = strongest(event.getCamera().getPosition());
        if (cloud == null || cloud.charge() <= 0) return;
        float depth = (float) cloud.charge();
        int color = cloud.style().color();
        event.setRed(Mth.lerp(depth, event.getRed(), (color >> 16 & 255) / 255f));
        event.setGreen(Mth.lerp(depth, event.getGreen(), (color >> 8 & 255) / 255f));
        event.setBlue(Mth.lerp(depth, event.getBlue(), (color & 255) / 255f));
    }

    public static void fogDistance(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        var cloud = strongest(event.getCamera().getPosition());
        if (cloud == null || cloud.charge() <= 0) return;
        float depth = (float) cloud.charge();
        event.setNearPlaneDistance(0);
        event.setFarPlaneDistance(Mth.lerp(depth, 4, 3));
        event.setCanceled(true);
    }
}
