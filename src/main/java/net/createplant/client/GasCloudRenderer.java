package net.createplant.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.HashCommon;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class GasCloudRenderer extends EntityRenderer<GasCloud> {
    private static final ResourceLocation TEXTURE = CreatePlant.location("textures/entity/gas_mist.png");
    private static final RenderType LAYER = MistRenderType.MIST;
    private final WeakHashMap<GasCloud, Mesh> meshes = new WeakHashMap<>();

    private record Wisp(float x, float y, float z, float width, float depth, float dx, float dz, float phase, boolean top) { }
    private record Mesh(CloudVolume volume, List<Wisp> wisps) { }

    private static final class MistRenderType extends RenderType {
        private static final RenderType MIST = create("create_refinery_n_reactors_gas_mist", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(TEXTURE, true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));

        private MistRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
            Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    public GasCloudRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(GasCloud cloud) { return TEXTURE; }

    @Override public void render(GasCloud cloud, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean preview = cloud.level() instanceof PonderLevel;
        double distance = preview ? 0 : cloud.distanceToSqr(entityRenderDispatcher.camera.getPosition());
        var style = cloud.style();
        if (style == null || style.color() == null) return;
        int alpha = (int) (106 * cloud.charge() * Mth.clamp((48 - Math.sqrt(distance)) / 16, 0, 1));
        if (alpha == 0) return;
        var mesh = meshes.get(cloud);
        if (mesh == null || mesh.volume() != cloud.volume()) {
            mesh = buildMesh(cloud.volume());
            meshes.put(cloud, mesh);
        }
        if (mesh.wisps().isEmpty()) return;
        var buffer = buffers.getBuffer(LAYER);
        var matrix = pose.last();
        float time = (cloud.tickCount + partialTick) * .045f;
        float topHeight = cloud.topHeight();
        int color = style.color();
        boolean ceiling = style.ceiling();
        for (var wisp : mesh.wisps()) {
            float height = wisp.top() ? topHeight : 1;
            float drift = Mth.sin(time + wisp.phase());
            float swell = Mth.cos(time * .72f + wisp.phase());
            int opacity = (int) (alpha * (.82f + swell * .18f));
            float low = .015f + height * (.05f + .035f * swell);
            float high = low + height * (.49f + .09f * drift);
            if (ceiling) {
                low = height - low;
                high = height - high;
            }
            float x = wisp.x() + wisp.width() * .055f * drift;
            float z = wisp.z() + wisp.depth() * .055f * swell;
            draw(buffer, matrix, wisp, x, z, low, high, wisp.dx(), wisp.dz(), color, opacity);
            draw(buffer, matrix, wisp, x, z, low, high, -wisp.dz(), wisp.dx(), color, opacity * 2 / 3);
        }
        if (preview && buffers instanceof SuperRenderTypeBuffer ponderBuffers) ponderBuffers.draw(LAYER);
    }

    private static Mesh buildMesh(CloudVolume volume) {
        var wisps = new ArrayList<Wisp>(volume.size());
        var origin = volume.origin();
        for (var pos : volume.positions()) {
            var relative = pos.subtract(origin);
            int west = volume.contains(pos.west()) ? 1 : 0;
            int east = volume.contains(pos.east()) ? 1 : 0;
            // Extend over a neighboring row only when it covers the whole wisp, including its corners.
            int north = spansRow(volume, pos.north(), west, east) ? 1 : 0;
            int south = spansRow(volume, pos.south(), west, east) ? 1 : 0;
            long seed = HashCommon.murmurHash3(pos.asLong());
            float phase = (seed >>> 48) * (Mth.TWO_PI / 65536f);
            float angle = (seed & 65535) * (Mth.TWO_PI / 65536f);
            float x = relative.getX() + (east - west) * .425f;
            float z = relative.getZ() + (south - north) * .425f;
            float width = .98f + (west + east) * .85f;
            float depth = .98f + (north + south) * .85f;
            wisps.add(new Wisp(x, relative.getY() - .5f, z, width, depth, Mth.cos(angle), Mth.sin(angle), phase,
                pos.getY() == volume.bounds().maxY - 1));
        }
        return new Mesh(volume, List.copyOf(wisps));
    }

    private static boolean spansRow(CloudVolume volume, BlockPos row, int west, int east) {
        return volume.contains(row) && (west == 0 || volume.contains(row.west())) && (east == 0 || volume.contains(row.east()));
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose matrix, Wisp wisp, float x, float z, float low, float high,
        float dx, float dz, int color, int alpha) {
        float alongX = dx * wisp.width() * .37f, alongZ = dz * wisp.depth() * .37f;
        float acrossX = -dz * wisp.width() * .24f, acrossZ = dx * wisp.depth() * .24f;
        vertex(buffer, matrix, x - alongX - acrossX, wisp.y() + low, z - alongZ - acrossZ, 0, 1, color, alpha);
        vertex(buffer, matrix, x + alongX - acrossX, wisp.y() + low, z + alongZ - acrossZ, 1, 1, color, alpha);
        vertex(buffer, matrix, x + alongX + acrossX, wisp.y() + high, z + alongZ + acrossZ, 1, 0, color, alpha);
        vertex(buffer, matrix, x - alongX + acrossX, wisp.y() + high, z - alongZ + acrossZ, 0, 0, color, alpha);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose matrix, float x, float y, float z, float u, float v, int color, int alpha) {
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
