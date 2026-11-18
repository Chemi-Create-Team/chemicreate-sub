package net.createplant.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createplant.CreatePlant;
import net.createplant.block.HighPressureTurbineMultiblock;
import net.createplant.block.HighPressureTurbinePartBlock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

public class SteamTurbineRenderer extends KineticBlockEntityRenderer<SteamTurbineBlockEntity> {
    private static final ResourceLocation LOW_PRESSURE_RENDER_DATA =
        CreatePlant.location("models/block/turbine/minecraft_realistic_impeller_turbine_render.json");
    private static final ResourceLocation HIGH_PRESSURE_EXACT_BLADE_RENDER_DATA =
        CreatePlant.location("models/block/turbine/high_pressure_turbine_blade_exact_render.json");
    private static final ResourceLocation HIGH_PRESSURE_EXACT_CASING_RENDER_DATA =
        CreatePlant.location("models/block/turbine/high_pressure_turbine_casing_exact_render.json");
    private static final ResourceLocation LOW_PRESSURE_CASING_RENDER_DATA =
        CreatePlant.location("models/block/turbine/low_pressure_turbine_casing_render.json");
    private static final float[] RADIUS_BY_SIZE = { 1.3f, 1.45f, 1.68f, 1.95f };
    private static final float SURFACE_OFFSET = 0.001f;
    private static final float LOW_PRESSURE_EXACT_CASING_SCALE = 5.0f / 3.0f;
    private static final RenderType STEAM_MIST_RENDER_TYPE =
        RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS, false);

    public SteamTurbineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SteamTurbineBlockEntity be, float partialTicks, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        if(be.unitLayout()!=null) {TurbineSectionRenderer.render(be,pose,buffers,light);return;}
        BlockState state = be.getBlockState();
        Direction.Axis axis = state.getValue(SteamTurbineBlock.AXIS);
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());

        TurbineShaftVisual.render(axis, shaftAngleForSpeed(be, axis, be.visualSpeed()), pose, solid, light);
        renderImpeller(be, axis, state, pose, solid, light);
        // The individual casing cells draw the closed shell; the rotor never draws a cutaway over it.
        renderSteamMist(be, axis, state, pose, buffers, light);
        if (!CEBlocks.isCasedSteamTurbine(state)) {
            TarbinEndRenderer.render(axis,
                be.hasTarbinEnd(Direction.AxisDirection.POSITIVE),
                be.hasTarbinEnd(Direction.AxisDirection.NEGATIVE),
                pose, buffers, light);
        }
    }

    public static void renderHighPressurePonderSection(Direction.Axis axis, int blades, boolean casing,
        PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        float renderTime = Minecraft.getInstance().level == null
            ? 0
            : AnimationTickHolder.getRenderTime(Minecraft.getInstance().level);
        float angle = (renderTime % 360.0f)
            / 180.0f * (float) Math.PI;

        TurbineShaftVisual.render(axis, angle, pose, solid, light);

        RenderMesh.Data bladeMesh = RenderMesh.load(HIGH_PRESSURE_EXACT_BLADE_RENDER_DATA);
        TextureAtlasSprite[] textures = highPressureBladeTextures();
        for (int i = 0; i < blades; i++) {
            float axisOffset = ((i + .5f) / blades) - .5f;
            renderHighPressureExactBlade(axis, angle, axisOffset, 1.0f / blades,
                bladeMesh, textures, pose, solid, light);
        }

        if (casing) {
            renderHighPressureExact(axis, 0, RenderMesh.load(HIGH_PRESSURE_EXACT_CASING_RENDER_DATA),
                casingTextures("block/turbine/high_pressure_casing"), pose, solid, light, true);
        }
    }

    public static void renderLowPressurePonderSection(Direction.Axis axis, int blades, BlockState state,
        BlockGetter level, BlockPos pos, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        float renderTime = Minecraft.getInstance().level == null
            ? 0
            : AnimationTickHolder.getRenderTime(Minecraft.getInstance().level);
        float angle = (renderTime % 360.0f)
            / 180.0f * (float) Math.PI;

        TurbineShaftVisual.render(axis, angle, pose, solid, light);

        RenderMesh.Data mesh = RenderMesh.load(LOW_PRESSURE_RENDER_DATA);
        TextureAtlasSprite[] textures = lowPressureTextures();
        if (mesh != null && mesh.quads != null) {
            for (int i = 0; i < blades; i++) {
                float axisOffset = ((i + .5f) / blades) - .5f;
                int size = state.getValue(SteamTurbineBlock.sizeProperty(i));
                renderImpellerBlade(axis, angle, axisOffset, radiusForSize(size), mesh, textures, pose, solid, light);
            }
        }

        if (level != null
            && (state.getValue(SteamTurbineBlock.CASING)
                || HighPressureTurbinePartBlock.isCompleteCasing(level, pos, axis,
                    HighPressureTurbineMultiblock.LOW_PRESSURE_RING_RADIUS))) {
            renderLowPressureFiveByFiveCasing(level, pos, axis, pose, solid, light);
        }
    }

    private static void renderSteamMist(SteamTurbineBlockEntity be, Direction.Axis axis, BlockState state,
        PoseStack pose, MultiBufferSource buffers, int light) {
        if (!CEBlocks.isCasedSteamTurbine(state) || be.getLevel() == null) {
            return;
        }

        HighPressureTurbineMultiblock.SteamStats steamStats =
            HighPressureTurbineMultiblock.assemblySteamStats(be.getLevel(), be.getBlockPos(), state);
        int steam = Math.max(steamStats.input(), Math.max(be.localSteamInput(), steamStats.buffer()));
        if (steam <= 0) {
            return;
        }

        TextureAtlasSprite mist = vanillaSprite("block/white_stained_glass");
        VertexConsumer buffer = buffers.getBuffer(STEAM_MIST_RENDER_TYPE);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        int alpha = Math.min(150, 72 + steam / 40);

        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        float drift = (time * .004f) % 1.0f;
        drawMistVolume(last, buffer, mist, .06f, .94f, drift, alpha, light);
        pose.popPose();
    }

    static void renderImpeller(SteamTurbineBlockEntity be, Direction.Axis axis, BlockState state,
        PoseStack pose, VertexConsumer buffer, int light) {
        int blades = state.getValue(SteamTurbineBlock.BLADES);
        float angle = shaftAngleForSpeed(be, axis, be.visualSpeed());
        if (CEBlocks.isHighPressureSteamTurbine(state)) {
            RenderMesh.Data mesh = RenderMesh.load(HIGH_PRESSURE_EXACT_BLADE_RENDER_DATA);
            TextureAtlasSprite[] textures = highPressureBladeTextures();
            for (int i = 0; i < blades; i++) {
                float axisOffset = ((i + .5f) / blades) - .5f;
                renderHighPressureExactBlade(axis, angle, axisOffset, 1.0f / blades,
                    mesh, textures, pose, buffer, light);
            }
            return;
        }
        RenderMesh.Data mesh = RenderMesh.load(LOW_PRESSURE_RENDER_DATA);
        if (mesh == null || mesh.quads == null)
            return;
        TextureAtlasSprite[] textures = lowPressureTextures();

        for (int i = 0; i < blades; i++) {
            float axisOffset = ((i + .5f) / blades) - .5f;
            int size = state.getValue(SteamTurbineBlock.sizeProperty(i));
            renderImpellerBlade(axis, angle, axisOffset, radiusForSize(size), mesh, textures, pose, buffer, light);
        }
    }

    private static void renderCasing(SteamTurbineBlockEntity be, Direction.Axis axis, BlockState state, PoseStack pose,
        VertexConsumer buffer, int light) {
        if (CEBlocks.isLowPressureSteamTurbine(state)) {
            if (be.getLevel() != null
                && HighPressureTurbinePartBlock.isCompleteCasing(be.getLevel(), be.getBlockPos(), axis,
                    HighPressureTurbineMultiblock.ringRadius(state))) {
                renderLowPressureFiveByFiveCasing(be.getLevel(), be.getBlockPos(), axis, pose, buffer, light);
            }
            return;
        }

        if (!state.getValue(SteamTurbineBlock.CASING)) {
            return;
        }

        renderHighPressureExact(axis, 0, RenderMesh.load(HIGH_PRESSURE_EXACT_CASING_RENDER_DATA),
            casingTextures("block/turbine/high_pressure_casing"), pose, buffer, light, true);
    }

    public static void renderLowPressureFiveByFiveCasing(BlockGetter level, BlockPos center, Direction.Axis axis,
        PoseStack pose, VertexConsumer buffer, int light) {
        RenderMesh.Data mesh = RenderMesh.load(LOW_PRESSURE_CASING_RENDER_DATA);
        TextureAtlasSprite[] textures = casingTextures("block/turbine/low_pressure_casing");
        if (mesh == null || mesh.quads == null) {
            return;
        }

        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.scale(1, LOW_PRESSURE_EXACT_CASING_SCALE, LOW_PRESSURE_EXACT_CASING_SCALE);
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        for (int firstQuad = 0; firstQuad < mesh.quads.size(); firstQuad += 6) {
            int lastQuad = Math.min(firstQuad + 6, mesh.quads.size());
            int ringX = panelCell(mesh.quads, firstQuad, lastQuad, 2);
            int ringY = panelCell(mesh.quads, firstQuad, lastQuad, 1);
            if (hasLowPressureWindow(level, center, axis, ringX, ringY)) {
                continue;
            }
            for (int i = firstQuad; i < lastQuad; i++) {
                RenderMesh.Quad quad = mesh.quads.get(i);
                int texture = quad.texture;
                if (texture < 0 || texture >= textures.length) {
                    continue;
                }
                RenderMesh.drawQuad(last, buffer, textures[texture], quad, light,
                    texture >= 1 ? SURFACE_OFFSET : 0);
                RenderMesh.drawQuadReversed(last, buffer, textures[texture], quad, light, SURFACE_OFFSET);
            }
        }
        pose.popPose();
    }

    private static boolean hasLowPressureWindow(BlockGetter level, BlockPos center, Direction.Axis axis,
        int ringX, int ringY) {
        if (ringX != 0 && ringY != 0 || ringX == 0 && ringY == 0) {
            return false;
        }
        int radius = HighPressureTurbineMultiblock.LOW_PRESSURE_RING_RADIUS;
        int casingRingX = (axis == Direction.Axis.Z ? -ringX : ringX) * radius;
        int casingRingY = ringY * radius;
        BlockState state = level.getBlockState(
            HighPressureTurbinePartBlock.partPos(center, axis, casingRingX, casingRingY));
        return HighPressureTurbinePartBlock.isCasingPart(state, axis, casingRingX, casingRingY)
            && state.getValue(HighPressureTurbinePartBlock.WINDOW);
    }

    private static int panelCell(List<RenderMesh.Quad> quads, int firstQuad, int lastQuad, int component) {
        float total = 0;
        int vertices = 0;
        for (int quadIndex = firstQuad; quadIndex < lastQuad; quadIndex++) {
            RenderMesh.Quad quad = quads.get(quadIndex);
            for (int i = 0; i < 4; i++) {
                total += quad.vertices[i * 3 + component];
                vertices++;
            }
        }
        float center = vertices == 0 ? .5f : total / vertices;
        if (center < 0) {
            return -1;
        }
        if (center > 1) {
            return 1;
        }
        return 0;
    }

    private static void renderImpellerBlade(Direction.Axis axis, float angle, float axisOffset, float radius,
        RenderMesh.Data mesh, TextureAtlasSprite[] textures, PoseStack pose, VertexConsumer buffer, int light) {
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.translate(axisOffset, 0, 0);
        pose.mulPose(new Quaternionf().rotateX(angle));
        pose.scale(1, radius, radius);
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        for (RenderMesh.Quad quad : mesh.quads) {
            int texture = quad.texture;
            if (texture < 0 || texture >= textures.length)
                continue;
            RenderMesh.drawQuad(last, buffer, textures[texture], quad, light, texture >= 2 ? SURFACE_OFFSET : 0);
        }
        pose.popPose();
    }

    private static void renderHighPressureExact(Direction.Axis axis, float angle, RenderMesh.Data mesh,
        TextureAtlasSprite[] textures, PoseStack pose, VertexConsumer buffer, int light, boolean doubleSided) {
        renderHighPressureExact(axis, angle, mesh, textures, pose, buffer, light, doubleSided, 1);
    }

    private static void renderHighPressureExact(Direction.Axis axis, float angle, RenderMesh.Data mesh,
        TextureAtlasSprite[] textures, PoseStack pose, VertexConsumer buffer, int light, boolean doubleSided,
        float radialScale) {
        if (mesh == null || mesh.quads == null) {
            return;
        }
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.mulPose(new Quaternionf().rotateX(angle));
        pose.scale(1, radialScale, radialScale);
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        for (RenderMesh.Quad quad : mesh.quads) {
            int texture = quad.texture;
            if (texture < 0 || texture >= textures.length) {
                continue;
            }
            RenderMesh.drawQuad(last, buffer, textures[texture], quad, light, texture >= 1 ? SURFACE_OFFSET : 0);
            if (doubleSided) {
                RenderMesh.drawQuadReversed(last, buffer, textures[texture], quad, light, SURFACE_OFFSET);
            }
        }
        pose.popPose();
    }

    private static void renderHighPressureExactBlade(Direction.Axis axis, float angle, float axisOffset,
        float axisScale, RenderMesh.Data mesh, TextureAtlasSprite[] textures, PoseStack pose, VertexConsumer buffer,
        int light) {
        if (mesh == null || mesh.quads == null) {
            return;
        }
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.translate(axisOffset, 0, 0);
        pose.mulPose(new Quaternionf().rotateX(angle));
        pose.scale(axisScale, 1, 1);
        pose.translate(-.5, -.5, -.5);

        PoseStack.Pose last = pose.last();
        for (RenderMesh.Quad quad : mesh.quads) {
            int texture = quad.texture;
            if (texture < 0 || texture >= textures.length) {
                continue;
            }
            RenderMesh.drawQuad(last, buffer, textures[texture], quad, light, texture >= 1 ? SURFACE_OFFSET : 0);
        }
        pose.popPose();
    }

    private static TextureAtlasSprite vanillaSprite(String path) {
        return Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation.withDefaultNamespace(path));
    }

    private static TextureAtlasSprite[] lowPressureTextures() {
        return new TextureAtlasSprite[] {
            RenderMesh.sprite("block/turbine/low_pressure_blade"),
            RenderMesh.sprite("block/turbine/rotor_disc")
        };
    }

    private static TextureAtlasSprite[] highPressureBladeTextures() {
        return new TextureAtlasSprite[] {
            RenderMesh.sprite("block/turbine/high_pressure_blade"),
            RenderMesh.sprite("block/turbine/rotor_disc")
        };
    }

    private static TextureAtlasSprite[] casingTextures(String casing) {
        return new TextureAtlasSprite[] {
            RenderMesh.sprite("block/turbine/rotor_disc"),
            RenderMesh.sprite(casing)
        };
    }

    private static void drawMistVolume(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        float x0, float x1, float drift, int alpha, int light) {
        float[][] points = {
            { 1.46f, .50f },
            { 1.20f, 1.20f },
            { .50f, 1.46f },
            { -.20f, 1.20f },
            { -.46f, .50f },
            { -.20f, -.20f },
            { .50f, -.46f },
            { 1.20f, -.20f }
        };
        for (int i = 0; i < points.length; i++) {
            float[] a = points[i];
            float[] b = points[(i + 1) % points.length];
            float normalY = (a[0] + b[0]) * .5f - .5f;
            float normalZ = (a[1] + b[1]) * .5f - .5f;
            float normalLength = Math.max(.001f, (float) Math.sqrt(normalY * normalY + normalZ * normalZ));
            drawMistFace(pose, buffer, sprite, x0, a[0], a[1], x1, a[0], a[1],
                x1, b[0], b[1], x0, b[0], b[1], drift, alpha, light,
                0, normalY / normalLength, normalZ / normalLength);
            drawMistFace(pose, buffer, sprite, x0, .5f, .5f, x0, b[0], b[1],
                x0, a[0], a[1], x0, .5f, .5f, drift, alpha, light, -1, 0, 0);
            drawMistFace(pose, buffer, sprite, x1, .5f, .5f, x1, a[0], a[1],
                x1, b[0], b[1], x1, .5f, .5f, drift, alpha, light, 1, 0, 0);
        }
    }

    private static void drawMistFace(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3,
        float drift, int alpha, int light, float normalX, float normalY, float normalZ) {
        mistVertex(pose, buffer, sprite, x0, y0, z0, 0, drift, alpha, light, normalX, normalY, normalZ);
        mistVertex(pose, buffer, sprite, x1, y1, z1, 1, drift, alpha, light, normalX, normalY, normalZ);
        mistVertex(pose, buffer, sprite, x2, y2, z2, 1, drift + 1, alpha, light, normalX, normalY, normalZ);
        mistVertex(pose, buffer, sprite, x3, y3, z3, 0, drift + 1, alpha, light, normalX, normalY, normalZ);
        mistVertex(pose, buffer, sprite, x3, y3, z3, 0, drift + 1, alpha, light, -normalX, -normalY, -normalZ);
        mistVertex(pose, buffer, sprite, x2, y2, z2, 1, drift + 1, alpha, light, -normalX, -normalY, -normalZ);
        mistVertex(pose, buffer, sprite, x1, y1, z1, 1, drift, alpha, light, -normalX, -normalY, -normalZ);
        mistVertex(pose, buffer, sprite, x0, y0, z0, 0, drift, alpha, light, -normalX, -normalY, -normalZ);
    }

    private static void mistVertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
        float x, float y, float z, float u, float v, int alpha, int light,
        float normalX, float normalY, float normalZ) {
        float wrappedU = u - (float) Math.floor(u);
        float wrappedV = v - (float) Math.floor(v);
        buffer.addVertex(pose, x, y, z)
            .setColor(235, 240, 240, alpha)
            .setUv(Mth.lerp(wrappedU, sprite.getU0(), sprite.getU1()),
                Mth.lerp(wrappedV, sprite.getV0(), sprite.getV1()))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float radiusForSize(int size) {
        return RADIUS_BY_SIZE[Math.max(1, Math.min(size, RADIUS_BY_SIZE.length)) - 1];
    }

    static float shaftAngleForSpeed(KineticBlockEntity be, Direction.Axis axis, float speed) {
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        return ((time * speed * 3.0f / 10.0f + offset) % 360.0f) / 180.0f * (float) Math.PI;
    }
}
