package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import java.util.List;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.fusion.FusionPhysics;
import net.createplant.fusion.FusionStructure;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Full-bright plasma following the centre of the vessel's two-by-two closed channel. */
public final class FusionReactorRenderer extends SafeBlockEntityRenderer<FusionReactorControllerBlockEntity> {
    private static final ResourceLocation PLASMA = CreatePlant.location("textures/block/fusion/plasma.png");
    private static final int MINOR_SEGMENTS = 12;

    public FusionReactorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(FusionReactorControllerBlockEntity be, float partialTicks, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay) {
        if (!be.formed() || be.plasmaKelvin() < 1_000_000) return;
        var path = FusionStructure.plasmaCentreline(be.structureWidth());
        var centre = be.centre();
        pose.pushPose();
        pose.translate(centre.getX() - be.getBlockPos().getX() + .5,
            centre.getY() - be.getBlockPos().getY() + .5,
            centre.getZ() - be.getBlockPos().getZ() + .5);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(PLASMA));
        float time = (be.getLevel().getGameTime() + partialTicks) * .018f;
        float intensity = (float) Math.clamp(be.plasmaKelvin() / FusionPhysics.PLASMA_KELVIN, 0, 1);
        int alpha = Math.round(95 + intensity * 110);
        for (int i = 0; i < path.size(); i++) for (int j = 0; j < MINOR_SEGMENTS; j++) {
            float u0 = i / (float) path.size(), u1 = (i + 1f) / path.size();
            float v0 = j / (float) MINOR_SEGMENTS, v1 = (j + 1f) / MINOR_SEGMENTS;
            point(path, vertices, matrix, i, u0, v0, time, alpha);
            point(path, vertices, matrix, i, u0, v1, time, alpha);
            point(path, vertices, matrix, (i + 1) % path.size(), u1, v1, time, alpha);
            point(path, vertices, matrix, (i + 1) % path.size(), u1, v0, time, alpha);
        }
        pose.popPose();
    }

    private static void point(List<Vec3> path, VertexConsumer vertices, Matrix4f matrix, int index, float u, float v, float time, int alpha) {
        Vec3 here = path.get(index);
        Vec3 before = path.get((index + path.size() - 1) % path.size());
        Vec3 after = path.get((index + 1) % path.size());
        double dx = after.x - before.x, dz = after.z - before.z;
        double length = Math.hypot(dx, dz), minor = v * Mth.TWO_PI;
        float nx = (float) (dz / length * Math.cos(minor));
        float ny = (float) Math.sin(minor);
        float nz = (float) (-dx / length * Math.cos(minor));
        // Centreline and elbows share the structure's 2x2 channel geometry.
        double radius = .72 + .025 * Math.sin(u * Mth.TWO_PI * 7 - time * 7);
        float x = (float) (here.x + radius * nx);
        float y = (float) (here.y + radius * ny);
        float z = (float) (here.z + radius * nz);
        vertices.addVertex(matrix, x, y, z).setColor(255, 105, 242, alpha)
            .setUv(u * 8 + time, v + time * .35f).setOverlay(0).setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
    }

    @Override public boolean shouldRenderOffScreen(FusionReactorControllerBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 96; }
}
