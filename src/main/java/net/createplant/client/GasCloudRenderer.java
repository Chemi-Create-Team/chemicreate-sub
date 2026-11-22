package net.createplant.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

public final class GasCloudRenderer extends EntityRenderer<GasCloud> {
    private static final ResourceLocation PLUME_TEXTURE = CreatePlant.location("textures/entity/gas_plume.png");
    private static final RenderType PLUME_LAYER = MistRenderType.layer("gas_plume", PLUME_TEXTURE);
    private static final VertexFormat GROUND_FORMAT = VertexFormat.builder().add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR).add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1).add("UV2", VertexFormatElement.UV2).build();
    private static final RenderType GROUND_LAYER = MistRenderType.groundLayer();
    private static ShaderInstance groundShader;
    private static final WeakHashMap<GasCloud, Mist> MISTS = new WeakHashMap<>();

    private static final class Mist {
        private CloudVolume volume;
        private FallingGasMist falling;
        private RisingGasMist plume;
        private float previousCharge, targetCharge, chargeChangedAt;
        private float refreshAt = Float.POSITIVE_INFINITY;
        private final long groundStartedAt;

        private Mist(GasCloud cloud) {
            previousCharge = targetCharge = (float) cloud.charge();
            groundStartedAt = cloud.level().getGameTime() - cloud.tickCount;
        }

        private FallingGasMist ground(GasCloud cloud, float time) {
            if (volume != cloud.volume() || time >= refreshAt) {
                refreshAt = falling == null && !(cloud.level() instanceof PonderLevel) ? time + 25 : Float.POSITIVE_INFINITY;
                volume = cloud.volume();
                falling = new FallingGasMist(cloud.level(), volume, falling, time);
            }
            return falling;
        }

        private float charge(float syncedCharge, float time) {
            float charge = Mth.lerp(Mth.clamp((time - chargeChangedAt) / 12, 0, 1), previousCharge, targetCharge);
            if (syncedCharge != targetCharge) {
                previousCharge = charge;
                targetCharge = syncedCharge;
                chargeChangedAt = time;
            }
            return charge;
        }
    }

    private static final class MistRenderType extends RenderType {
        private static RenderType groundLayer() {
            return create(CreatePlant.MOD_ID + "_ground_gas", GROUND_FORMAT,
                VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                .setShaderState(new ShaderStateShard(() -> groundShader))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
        }

        private static RenderType layer(String name, ResourceLocation texture) {
            return create(CreatePlant.MOD_ID + "_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));
        }

        private MistRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
            Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), CreatePlant.location("ground_gas"),
                GROUND_FORMAT), shader -> groundShader = shader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public GasCloudRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(GasCloud cloud) {
        return PLUME_TEXTURE;
    }

    @Override public boolean shouldRender(GasCloud cloud, Frustum frustum, double x, double y, double z) {
        var style = cloud.style();
        if (style == null || !style.ceiling() || style.color() == null) return false;
        if (super.shouldRender(cloud, frustum, x, y, z)) return true;
        return cloud.shouldRender(x, y, z)
            && frustum.isVisible(cloud.getBoundingBoxForCulling().inflate(RisingGasMist.SKY_SPREAD, 0, RisingGasMist.SKY_SPREAD)
                .expandTowards(0, RisingGasMist.SKY_RISE, 0));
    }

    @Override public void render(GasCloud cloud, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean preview = cloud.level() instanceof PonderLevel;
        double distance = preview ? 0 : cloud.distanceToSqr(entityRenderDispatcher.camera.getPosition());
        var style = cloud.style();
        if (style == null || style.color() == null || !preview && !style.ceiling()) return;
        float distanceFade = (float) Mth.clamp((48 - Math.sqrt(distance)) / 16, 0, 1);
        if (distanceFade == 0) return;
        var mist = MISTS.computeIfAbsent(cloud, Mist::new);
        float time = cloud.tickCount + partialTick;
        float charge = mist.charge((float) cloud.charge(), time);
        var volume = cloud.volume();
        if (volume.size() == 0 || charge <= 0) return;
        int color = style.color();
        var layer = style.ceiling() ? PLUME_LAYER : GROUND_LAYER;
        var buffer = buffers.getBuffer(layer);
        var matrix = pose.last();
        var billboard = GasBillboard.facing(matrix, entityRenderDispatcher.cameraOrientation(), preview);
        if (style.ceiling()) {
            if (mist.volume != volume) {
                mist.volume = volume;
                mist.plume = new RisingGasMist(volume);
            }
            mist.plume.render(cloud.level(), preview, buffer, matrix, billboard, time, charge, distanceFade, color);
        } else {
            var view = new Vector3f(billboard.right()).cross(billboard.up());
            mist.ground(cloud, time).render(buffer, matrix, null, null, view, time, charge, color);
        }
        if (preview && buffers instanceof SuperRenderTypeBuffer ponderBuffers) ponderBuffers.draw(layer);
    }

    public static void renderGroundMist(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var camera = event.getCamera().getPosition();
        var frustum = event.getFrustum();
        var pose = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = null;
        // Entity rendering checks whether the source's chunk section was drawn. A mist can be far outside that section.
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof GasCloud cloud) || cloud.volume().size() == 0) continue;
            var style = cloud.style();
            if (style == null || style.ceiling() || style.color() == null) continue;
            var mist = MISTS.computeIfAbsent(cloud, Mist::new);
            float time = (level.getGameTime() - mist.groundStartedAt) + event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float charge = mist.charge((float) cloud.charge(), time);
            if (charge <= 0) continue;
            var ground = mist.ground(cloud, time);
            ground.spread(level, time);
            if (ground.bounds().distanceToSqr(camera) >= 48 * 48 || !frustum.isVisible(ground.bounds())) continue;
            if (buffer == null) buffer = buffers.getBuffer(GROUND_LAYER);
            var anchor = Vec3.atCenterOf(cloud.volume().origin());
            pose.pushPose();
            pose.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
            ground.render(buffer, pose.last(), camera, frustum, null, time, charge, style.color());
            pose.popPose();
        }
        if (buffer != null) buffers.endBatch(GROUND_LAYER);
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float x, float y, float z, float u, float v, int color, int alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(matrix, 0, 1, 0);
    }

    private static GasCloud strongest(Vec3 position) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var box = AABB.ofSize(position, .002, .002, .002);
        GasCloud strongest = null;
        for (var cloud : GasClouds.in(level, box)) {
            var style = cloud.style();
            if (style != null && style.ceiling() && style.color() != null && cloud.contains(position)
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
