package net.createplant.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createplant.CreatePlant;
import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.HighPressureTurbinePartBlock;
import net.createplant.block.entity.HighPressureTurbinePartBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

public class HighPressureTurbinePartRenderer implements BlockEntityRenderer<HighPressureTurbinePartBlockEntity> {
    private static final ResourceLocation RENDER_DATA =
        CreatePlant.location("models/block/turbine/high_pressure_turbine_casing_exact_render.json");
    private static final RenderType GLASS_RENDER_TYPE = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS, false);
    private static final float SURFACE_OFFSET = 0.001f;
    private static final float LOW_PRESSURE_PANEL_THICKNESS = 0.2419f;
    private static final float LOW_PRESSURE_PANEL_HALF_SPAN = 1.138f;

    public HighPressureTurbinePartRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HighPressureTurbinePartBlockEntity be, float partialTick, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if(state.getValue(HighPressureTurbinePartBlock.SECTION))return;
        if(state.getValue(HighPressureTurbinePartBlock.ATTACHED) && !state.getValue(HighPressureTurbinePartBlock.WINDOW)) {
            return;
        }
        if (state.getValue(HighPressureTurbinePartBlock.ATTACHED) && be.getLevel() != null
            && HighPressureTurbinePartBlock.isLowPressureCasingCenter(be.getLevel(),
                HighPressureTurbinePartBlock.centerPos(be.getBlockPos(), state))) {
            BlockPos center = HighPressureTurbinePartBlock.centerPos(be.getBlockPos(), state);
            Direction.Axis axis = state.getValue(HighPressureTurbinePartBlock.AXIS);
            boolean complete = HighPressureTurbinePartBlock.isCompleteCasing(be.getLevel(), center, axis,
                HighPressureTurbineMultiblock.LOW_PRESSURE_RING_RADIUS);
            if (!complete || state.getValue(HighPressureTurbinePartBlock.WINDOW)) {
                renderLowPressurePart(state, pose, buffers, light);
            }
            return;
        }
        renderPonderPart(state, pose, buffers, light);
    }

    private static void renderLowPressurePart(BlockState state, PoseStack pose, MultiBufferSource buffers,
        int light) {
        RenderMesh.Data mesh = RenderMesh.load(RENDER_DATA);
        if (mesh == null || mesh.quads == null) {
            return;
        }

        int ringX = HighPressureTurbineEndBlock.ringOffsetFromState(
            state.getValue(HighPressureTurbinePartBlock.RING_X));
        int ringY = HighPressureTurbineEndBlock.ringOffsetFromState(
            state.getValue(HighPressureTurbinePartBlock.RING_Y));
        Direction.Axis axis = state.getValue(HighPressureTurbinePartBlock.AXIS);
        if (axis == Direction.Axis.Z) {
            ringX = -ringX;
        }

        int radius = HighPressureTurbineMultiblock.LOW_PRESSURE_RING_RADIUS;
        int panelRingX;
        int panelRingY;
        if (Math.abs(ringX) == radius && Math.abs(ringY) == radius) {
            panelRingX = Integer.signum(ringX);
            panelRingY = Integer.signum(ringY);
        } else if (Math.abs(ringY) == radius) {
            panelRingX = 0;
            panelRingY = Integer.signum(ringY);
        } else {
            panelRingX = Integer.signum(ringX);
            panelRingY = 0;
        }

        TextureAtlasSprite[] textures = textures("block/turbine/low_pressure_casing");
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        boolean window = state.getValue(HighPressureTurbinePartBlock.WINDOW)
            && HighPressureTurbinePartBlock.canHaveWindow(state);
        if (window && textures.length > 2) {
            PanelBasis basis = lowPressurePanelBasis(panelRingX, panelRingY);
            drawLowPressureWindowPanel(last, solid, textures[1], basis, light);
            drawGlassDetails(last, solid, textures[1], basis, light);
            drawGlassBox(last, buffers.getBuffer(GLASS_RENDER_TYPE), textures[2], basis, light);
        } else {
            drawExactPanelAt(last, solid, textures, mesh,
                panelRingX, panelRingY, -panelRingY, -panelRingX, light);
        }
        pose.popPose();
    }

    public static void renderPonderPart(BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!state.getValue(HighPressureTurbinePartBlock.CASING)) {
            return;
        }

        RenderMesh.Data mesh = RenderMesh.load(RENDER_DATA);
        if (mesh == null || mesh.quads == null) {
            return;
        }

        boolean attached = state.getValue(HighPressureTurbinePartBlock.ATTACHED);
        int ringX = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(HighPressureTurbinePartBlock.RING_X));
        int ringY = HighPressureTurbineEndBlock.ringOffsetFromState(state.getValue(HighPressureTurbinePartBlock.RING_Y));
        if (!attached && ringX == 0 && ringY == 0) {
            ringY = 1;
        }
        Direction.Axis axis = state.getValue(HighPressureTurbinePartBlock.AXIS);
        if (attached && axis == Direction.Axis.Z) {
            ringX = -ringX;
        }
        TextureAtlasSprite[] textures = textures("block/turbine/high_pressure_casing");
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());

        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        boolean window = state.getValue(HighPressureTurbinePartBlock.WINDOW)
            && HighPressureTurbinePartBlock.canHaveWindow(state);
        if (window && textures.length > 2) {
            drawWindowPanel(last, solid, textures[1], ringX, ringY, light);
            drawGlassDetails(last, solid, textures[1], ringX, ringY, light);
            VertexConsumer glassBuffer = buffers.getBuffer(GLASS_RENDER_TYPE);
            drawGlassBox(last, glassBuffer, textures[2], ringX, ringY, light);
        } else {
            drawExactPanel(last, solid, textures, mesh, ringX, ringY, light);
        }
        pose.popPose();
    }

    private static void drawExactPanel(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite[] textures,
        RenderMesh.Data mesh, int ringX, int ringY, int light) {
        int meshRingX = Math.max(-1, Math.min(1, ringX));
        int meshRingY = Math.max(-1, Math.min(1, ringY));
        drawExactPanelAt(pose, buffer, textures, mesh,
            meshRingX, meshRingY, -ringY, -ringX, light);
    }

    private static void drawExactPanelAt(PoseStack.Pose pose, VertexConsumer buffer,
        TextureAtlasSprite[] textures, RenderMesh.Data mesh, int meshRingX, int meshRingY,
        int offsetY, int offsetZ, int light) {
        for (int firstQuad = 0; firstQuad < mesh.quads.size(); firstQuad += 6) {
            int lastQuad = Math.min(firstQuad + 6, mesh.quads.size());
            if (panelCellX(mesh.quads, firstQuad, lastQuad) != meshRingX
                || panelCellY(mesh.quads, firstQuad, lastQuad) != meshRingY) {
                continue;
            }
            for (int i = firstQuad; i < lastQuad; i++) {
                RenderMesh.Quad quad = mesh.quads.get(i);
                if (quad.texture < 0 || quad.texture >= textures.length) {
                    continue;
                }
                RenderMesh.Quad localQuad = quad.offset(0, offsetY, offsetZ);
                RenderMesh.drawQuad(pose, buffer, textures[localQuad.texture], localQuad, light, SURFACE_OFFSET);
                RenderMesh.drawQuadReversed(pose, buffer, textures[localQuad.texture], localQuad, light,
                    SURFACE_OFFSET);
            }
        }
    }

    private static int panelCellX(List<RenderMesh.Quad> quads, int firstQuad, int lastQuad) {
        return cellFor(panelCenter(quads, firstQuad, lastQuad, 2));
    }

    private static int panelCellY(List<RenderMesh.Quad> quads, int firstQuad, int lastQuad) {
        return cellFor(panelCenter(quads, firstQuad, lastQuad, 1));
    }

    private static float panelCenter(List<RenderMesh.Quad> quads, int firstQuad, int lastQuad, int component) {
        float total = 0;
        int vertices = 0;
        for (int quadIndex = firstQuad; quadIndex < lastQuad; quadIndex++) {
            RenderMesh.Quad quad = quads.get(quadIndex);
            for (int i = 0; i < 4; i++) {
                total += quad.vertices[i * 3 + component];
                vertices++;
            }
        }
        return vertices == 0 ? 0 : total / vertices;
    }

    private static int cellFor(float value) {
        if (value < 0) {
            return -1;
        }
        if (value > 1) {
            return 1;
        }
        return 0;
    }

    private static TextureAtlasSprite[] textures(String casing) {
        return new TextureAtlasSprite[] {
            RenderMesh.sprite("block/turbine/rotor_disc"),
            RenderMesh.sprite(casing),
            net.minecraft.client.Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/glass"))
        };
    }

    private static void drawWindowPanel(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        int ringX, int ringY, int light) {
        drawWindowPanel(pose, buffer, sprite, panelBasis(ringX, ringY), light);
    }

    private static void drawWindowPanel(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        PanelBasis basis, int light) {
        drawFrameBox(pose, buffer, sprite, basis, .00f, .06f, -.68f, .68f, light);
        drawFrameBox(pose, buffer, sprite, basis, .94f, 1.00f, -.68f, .68f, light);
        drawFrameBox(pose, buffer, sprite, basis, .06f, .94f, .60f, .68f, light);
        drawFrameBox(pose, buffer, sprite, basis, .06f, .94f, -.68f, -.60f, light);
        drawFrameBox(pose, buffer, sprite, basis, .06f, .10f, -.60f, .60f, light);
        drawFrameBox(pose, buffer, sprite, basis, .90f, .94f, -.60f, .60f, light);
        drawFrameBox(pose, buffer, sprite, basis, .10f, .90f, .54f, .60f, light);
        drawFrameBox(pose, buffer, sprite, basis, .10f, .90f, -.60f, -.54f, light);
    }

    private static void drawLowPressureWindowPanel(PoseStack.Pose pose, VertexConsumer buffer,
        TextureAtlasSprite sprite, PanelBasis basis, int light) {
        float outer = LOW_PRESSURE_PANEL_HALF_SPAN;
        drawFrameBox(pose, buffer, sprite, basis, .00f, .10f, -outer, outer, light);
        drawFrameBox(pose, buffer, sprite, basis, .90f, 1.00f, -outer, outer, light);
        drawFrameBox(pose, buffer, sprite, basis, .10f, .90f, .535f, outer, light);
        drawFrameBox(pose, buffer, sprite, basis, .10f, .90f, -outer, -.535f, light);
    }

    private static void drawGlassBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        int ringX, int ringY, int light) {
        drawGlassBox(pose, buffer, sprite, panelBasis(ringX, ringY), light);
    }

    private static void drawGlassBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        PanelBasis basis, int light) {
        RenderMesh.Quad[] quads = orientedBox(basis, .105f, .895f, -.535f, .535f,
            -basis.thickness() + .012f, basis.thickness() + .012f);
        for (int i = 0; i < quads.length; i++) {
            if (i >= 2) {
                continue;
            }
            RenderMesh.Quad quad = quads[i];
            int alpha = 90;
            RenderMesh.drawQuad(pose, buffer, sprite, quad, light, SURFACE_OFFSET, 210, 232, 235, alpha);
            RenderMesh.drawQuadReversed(pose, buffer, sprite, quad, light, SURFACE_OFFSET, 210, 232, 235, alpha);
        }
    }

    private static void drawGlassDetails(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        int ringX, int ringY, int light) {
        drawGlassDetails(pose, buffer, sprite, panelBasis(ringX, ringY), light);
    }

    private static void drawGlassDetails(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        PanelBasis basis, int light) {
        drawDetailQuad(pose, buffer, sprite, orientedQuad(basis, .485f, .515f, -.53f, .53f, basis.thickness() + .010f),
            light, 205, 215, 215);
        drawDetailQuad(pose, buffer, sprite, orientedQuad(basis, .15f, .25f, .40f, .50f, basis.thickness() + .011f),
            light, 165, 195, 198);
        drawDetailQuad(pose, buffer, sprite, orientedQuad(basis, .75f, .85f, -.50f, -.40f, basis.thickness() + .011f),
            light, 165, 195, 198);
    }

    private static void drawDetailQuad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        RenderMesh.Quad quad, int light, int red, int green, int blue) {
        RenderMesh.drawQuad(pose, buffer, sprite, quad, light, SURFACE_OFFSET, red, green, blue, 255);
        RenderMesh.drawQuadReversed(pose, buffer, sprite, quad, light, SURFACE_OFFSET, red, green, blue, 255);
    }

    private static void drawFrameBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        PanelBasis basis, float xMin, float xMax, float tMin, float tMax, int light) {
        drawBox(pose, buffer, sprite, basis, xMin, xMax, tMin, tMax, light);
    }

    private static void drawBox(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        PanelBasis basis, float xMin, float xMax, float tMin, float tMax, int light) {
        for (RenderMesh.Quad quad : orientedBox(basis, xMin, xMax, tMin, tMax, -basis.thickness(), basis.thickness())) {
            RenderMesh.drawQuad(pose, buffer, sprite, quad, light, SURFACE_OFFSET);
            RenderMesh.drawQuadReversed(pose, buffer, sprite, quad, light, SURFACE_OFFSET);
        }
    }

    private static PanelBasis panelBasis(int ringX, int ringY) {
        if (ringY > 0 && ringX == 0) {
            return new PanelBasis(.5f, .85486f, .5f, 0, 1, 0, 0, 0, 1, .14514f);
        }
        if (ringY < 0 && ringX == 0) {
            return new PanelBasis(.5f, .14514f, .5f, 0, -1, 0, 0, 0, 1, .14514f);
        }
        if (ringX > 0 && ringY == 0) {
            return new PanelBasis(.5f, .5f, .85486f, 0, 0, 1, 0, 1, 0, .14514f);
        }
        if (ringX < 0 && ringY == 0) {
            return new PanelBasis(.5f, .5f, .14514f, 0, 0, -1, 0, 1, 0, .14514f);
        }

        float invSqrt2 = .707107f;
        float normalY = Math.signum(ringY) * invSqrt2;
        float normalZ = Math.signum(ringX) * invSqrt2;
        float tangentY = -Math.signum(ringY) * invSqrt2;
        float tangentZ = Math.signum(ringX) * invSqrt2;
        return new PanelBasis(.5f, .5f, .5f, 0, normalY, normalZ, 0, tangentY, tangentZ, .14514f);
    }

    private static PanelBasis lowPressurePanelBasis(int ringX, int ringY) {
        float thickness = LOW_PRESSURE_PANEL_THICKNESS;
        if (ringY > 0 && ringX == 0) {
            return new PanelBasis(.5f, 1 - thickness, .5f, 0, 1, 0, 0, 0, 1, thickness);
        }
        if (ringY < 0 && ringX == 0) {
            return new PanelBasis(.5f, thickness, .5f, 0, -1, 0, 0, 0, 1, thickness);
        }
        if (ringX > 0 && ringY == 0) {
            return new PanelBasis(.5f, .5f, 1 - thickness, 0, 0, 1, 0, 1, 0, thickness);
        }
        return new PanelBasis(.5f, .5f, thickness, 0, 0, -1, 0, 1, 0, thickness);
    }

    private static RenderMesh.Quad orientedQuad(PanelBasis basis, float xMin, float xMax, float tMin, float tMax,
        float normalOffset) {
        float[] v0 = basis.point(xMin, tMin, normalOffset);
        float[] v1 = basis.point(xMax, tMin, normalOffset);
        float[] v2 = basis.point(xMax, tMax, normalOffset);
        float[] v3 = basis.point(xMin, tMax, normalOffset);
        return quad(v0, v1, v2, v3, basis.normal());
    }

    private static RenderMesh.Quad[] orientedBox(PanelBasis basis, float xMin, float xMax, float tMin, float tMax,
        float nMin, float nMax) {
        float[] x0t0n0 = basis.point(xMin, tMin, nMin);
        float[] x1t0n0 = basis.point(xMax, tMin, nMin);
        float[] x1t1n0 = basis.point(xMax, tMax, nMin);
        float[] x0t1n0 = basis.point(xMin, tMax, nMin);
        float[] x0t0n1 = basis.point(xMin, tMin, nMax);
        float[] x1t0n1 = basis.point(xMax, tMin, nMax);
        float[] x1t1n1 = basis.point(xMax, tMax, nMax);
        float[] x0t1n1 = basis.point(xMin, tMax, nMax);
        return new RenderMesh.Quad[] {
            quad(x0t0n1, x1t0n1, x1t1n1, x0t1n1, basis.normal()),
            quad(x1t0n0, x0t0n0, x0t1n0, x1t1n0, basis.inverseNormal()),
            quad(x0t1n0, x0t1n1, x1t1n1, x1t1n0, basis.tangent()),
            quad(x0t0n1, x0t0n0, x1t0n0, x1t0n1, basis.inverseTangent()),
            quad(x1t0n1, x1t0n0, x1t1n0, x1t1n1, new float[] {1, 0, 0}),
            quad(x0t0n0, x0t0n1, x0t1n1, x0t1n0, new float[] {-1, 0, 0})
        };
    }

    private static RenderMesh.Quad quad(float[] v0, float[] v1, float[] v2, float[] v3, float[] normal) {
        RenderMesh.Quad quad = new RenderMesh.Quad();
        quad.texture = 1;
        quad.vertices = new float[] {
            v0[0], v0[1], v0[2],
            v1[0], v1[1], v1[2],
            v2[0], v2[1], v2[2],
            v3[0], v3[1], v3[2]
        };
        // The plain part of a casing sprite: window frames are too narrow to show its flanges and bolts.
        quad.uv = new float[] {.1875f, .09375f, .375f, .09375f, .375f, .15625f, .1875f, .15625f};
        quad.normal = normal;
        return quad;
    }

    private record PanelBasis(float centerX, float centerY, float centerZ, float normalX, float normalY,
        float normalZ, float tangentX, float tangentY, float tangentZ, float thickness) {
        private float[] point(float x, float tangentOffset, float normalOffset) {
            return new float[] {
                centerX + x - .5f + tangentX * tangentOffset + normalX * normalOffset,
                centerY + tangentY * tangentOffset + normalY * normalOffset,
                centerZ + tangentZ * tangentOffset + normalZ * normalOffset
            };
        }

        private float[] normal() {
            return new float[] {normalX, normalY, normalZ};
        }

        private float[] inverseNormal() {
            return new float[] {-normalX, -normalY, -normalZ};
        }

        private float[] tangent() {
            return new float[] {tangentX, tangentY, tangentZ};
        }

        private float[] inverseTangent() {
            return new float[] {-tangentX, -tangentY, -tangentZ};
        }
    }
}
