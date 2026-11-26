package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createplant.CreatePlant;
import net.createplant.contraptions.FactoryBodyEntity;
import net.createplant.factory.FactoryGeometry;
import net.createplant.factory.FactoryPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class FactoryBodyRenderer extends EntityRenderer<FactoryBodyEntity> {
    public FactoryBodyRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(FactoryBodyEntity body) { return CreatePlant.location("textures/block/continuous_factory/steel.png"); }

    @Override public void render(FactoryBodyEntity body, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        if (!body.hasPose() || body.geometry() == null) return;
        boolean fallback = !VisualizationManager.supportsVisualization(body.level());
        boolean debug = Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes() && !Minecraft.getInstance().showOnlyReducedInfo();
        if (!fallback && !debug) return;
        var pose = body.pose(partial);
        draw(body.geometry(), pose, body.getPosition(partial), stack, buffers, light, fallback, debug);
    }

    static void draw(FactoryGeometry geometry, FactoryPose pose, Vec3 origin, PoseStack stack, MultiBufferSource buffers,
                     int light, boolean model, boolean debug) {
        stack.pushPose();
        Vec3 center = pose.center().subtract(origin);
        stack.translate(center.x, center.y, center.z);
        stack.mulPose(pose.rotation());
        stack.scale(pose.width(), pose.height(), pose.length());
        if (model) FactoryBodyMesh.get(geometry, pose.length()).render(stack, buffers.getBuffer(RenderType.solid()), light);
        if (debug) {
            var lines = buffers.getBuffer(RenderType.lines());
            for (var piece : geometry.pieces()) LevelRenderer.renderLineBox(stack, lines, piece.bounds(), 1, .57f, .16f, .9f);
        }
        stack.popPose();
    }
}
