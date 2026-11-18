package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createplant.CreatePlant;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

public final class TarbinEndRenderer {
    private static final ResourceLocation RENDER_DATA =
        CreatePlant.location("models/block/turbine/tarbin_end_render.json");
    private static final float SURFACE_OFFSET = 0.001f;
    private static final float DIAMETER_SCALE = 0.7f;

    private TarbinEndRenderer() {
    }

    public static void render(Direction.Axis axis, boolean positive, boolean negative, PoseStack pose,
        MultiBufferSource buffers, int light) {
        RenderMesh.Data mesh = RenderMesh.load(RENDER_DATA);
        if (mesh == null || mesh.quads == null) {
            return;
        }

        VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        TextureAtlasSprite[] textures = textures();
        if (positive) {
            renderOne(axis, true, mesh, textures, pose, solid, light);
        }
        if (negative) {
            renderOne(axis, false, mesh, textures, pose, solid, light);
        }
    }

    private static void renderOne(Direction.Axis axis, boolean positive, RenderMesh.Data mesh,
        TextureAtlasSprite[] textures, PoseStack pose, VertexConsumer buffer, int light) {
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        if (!positive) {
            pose.mulPose(new Quaternionf().rotateY((float) Math.PI));
        }
        pose.translate(.25, 0, 0);
        pose.scale(1, DIAMETER_SCALE, DIAMETER_SCALE);
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        for (RenderMesh.Quad quad : mesh.quads) {
            int texture = quad.texture;
            if (texture < 0 || texture >= textures.length) {
                continue;
            }
            RenderMesh.drawQuad(last, buffer, textures[texture], quad, light, SURFACE_OFFSET);
            RenderMesh.drawQuadReversed(last, buffer, textures[texture], quad, light, SURFACE_OFFSET);
        }
        pose.popPose();
    }

    private static TextureAtlasSprite[] textures() {
        return new TextureAtlasSprite[] {
            RenderMesh.sprite("block/turbine/rotor_disc"),
            RenderMesh.sprite("block/turbine/high_pressure_casing")
        };
    }
}
