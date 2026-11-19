package net.createplant.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class GasCloudRenderer extends EntityRenderer<GasCloud> {
    private static final ResourceLocation PLUME_TEXTURE = CreatePlant.location("textures/entity/gas_plume.png");
    private static final RenderType PLUME_LAYER = MistRenderType.layer("gas_plume", PLUME_TEXTURE);
    private final WeakHashMap<GasCloud, Mist> mists = new WeakHashMap<>();

    private static final class Mist {
        private CloudVolume volume;
        private FallingGasMist falling;
        private RisingGasMist plume;
        private float previousCharge, targetCharge, chargeChangedAt;

        private Mist(GasCloud cloud) {
            previousCharge = targetCharge = (float) cloud.charge();
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

    public GasCloudRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(GasCloud cloud) {
        return PLUME_TEXTURE;
    }

    @Override public boolean shouldRender(GasCloud cloud, Frustum frustum, double x, double y, double z) {
        if (super.shouldRender(cloud, frustum, x, y, z)) return true;
        var style = cloud.style();
        return style != null && style.ceiling() && style.color() != null && cloud.shouldRender(x, y, z)
            && frustum.isVisible(cloud.getBoundingBoxForCulling().inflate(RisingGasMist.SKY_SPREAD, 0, RisingGasMist.SKY_SPREAD)
                .expandTowards(0, RisingGasMist.SKY_RISE, 0));
    }

    @Override public void render(GasCloud cloud, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean preview = cloud.level() instanceof PonderLevel;
        double distance = preview ? 0 : cloud.distanceToSqr(entityRenderDispatcher.camera.getPosition());
        var style = cloud.style();
        if (style == null || style.color() == null) return;
        float distanceFade = (float) Mth.clamp((48 - Math.sqrt(distance)) / 16, 0, 1);
        if (distanceFade == 0) return;
        var mist = mists.computeIfAbsent(cloud, Mist::new);
        float time = cloud.tickCount + partialTick;
        float charge = mist.charge((float) cloud.charge(), time);
        var volume = cloud.volume();
        if (volume.size() == 0 || charge <= 0) return;
        if (mist.volume != volume) {
            mist.volume = volume;
            if (style.ceiling()) mist.plume = new RisingGasMist(volume);
            else mist.falling = new FallingGasMist(cloud.level(), volume);
        }
        int color = style.color();
        var buffer = buffers.getBuffer(PLUME_LAYER);
        var matrix = pose.last();
        var billboard = GasBillboard.facing(matrix, entityRenderDispatcher.cameraOrientation(), preview);
        if (style.ceiling()) {
            mist.plume.render(cloud.level(), preview, buffer, matrix, billboard, time, charge, distanceFade, color);
        } else {
            int top = (int) volume.bounds().maxY - 1 - volume.origin().getY();
            mist.falling.render(buffer, matrix, billboard, time, charge, distanceFade, top, cloud.topHeight(), color);
        }
        if (preview && buffers instanceof SuperRenderTypeBuffer ponderBuffers) ponderBuffers.draw(PLUME_LAYER);
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
