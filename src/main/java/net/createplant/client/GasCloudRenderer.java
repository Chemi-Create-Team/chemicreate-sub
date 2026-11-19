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
    private final WeakHashMap<GasCloud, Mist> mists = new WeakHashMap<>();

    private record Wisp(float x, float y, float z, float width, float depth, float phase, float speed, float bend, boolean top) { }

    private static final class Mist {
        private final float direction;
        private CloudVolume volume;
        private List<Wisp> wisps = List.of();
        private float previousCharge, targetCharge, chargeChangedAt;

        private Mist(GasCloud cloud) {
            direction = (HashCommon.murmurHash3(cloud.blockPosition().asLong()) >>> 48) * (Mth.TWO_PI / 65536f);
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
        float distanceFade = (float) Mth.clamp((48 - Math.sqrt(distance)) / 16, 0, 1);
        if (distanceFade == 0) return;
        var mist = mists.computeIfAbsent(cloud, Mist::new);
        float time = cloud.tickCount + partialTick;
        float charge = mist.charge((float) cloud.charge(), time);
        int alpha = (int) (128 * charge * distanceFade);
        if (alpha == 0) return;
        if (mist.volume != cloud.volume()) {
            mist.volume = cloud.volume();
            mist.wisps = buildWisps(mist.volume);
        }
        if (mist.wisps.isEmpty()) return;
        var buffer = buffers.getBuffer(LAYER);
        var matrix = pose.last();
        float topHeight = cloud.topHeight();
        int color = style.color();
        boolean ceiling = style.ceiling();
        for (var wisp : mist.wisps) {
            float height = wisp.top() ? topHeight : 1;
            drawWisp(buffer, matrix, wisp, time, mist.direction, height, ceiling, color, alpha);
        }
        if (preview && buffers instanceof SuperRenderTypeBuffer ponderBuffers) ponderBuffers.draw(LAYER);
    }

    private static List<Wisp> buildWisps(CloudVolume volume) {
        var wisps = new ArrayList<Wisp>(volume.size());
        var origin = volume.origin();
        for (var pos : volume.positions()) {
            var relative = pos.subtract(origin);
            int west = extent(volume, pos, -1, 0, 0, 0), east = extent(volume, pos, 1, 0, 0, 0);
            int north = extent(volume, pos, 0, -1, west, east), south = extent(volume, pos, 0, 1, west, east);
            long seed = HashCommon.murmurHash3(pos.asLong());
            float phase = (seed >>> 48) / 65536f;
            float speed = .014f + ((seed >>> 24) & 255) * (.01f / 255);
            float bend = (seed & 255) * (2f / 255) - 1;
            float x = relative.getX() + (east - west) * .5f;
            float z = relative.getZ() + (south - north) * .5f;
            float width = .98f + west + east, depth = .98f + north + south;
            wisps.add(new Wisp(x, relative.getY() - .5f, z, width, depth, phase, speed, bend,
                pos.getY() == volume.bounds().maxY - 1));
        }
        return List.copyOf(wisps);
    }

    private static int extent(CloudVolume volume, BlockPos pos, int dx, int dz, int west, int east) {
        var cell = new BlockPos.MutableBlockPos();
        for (int reach = 1; reach <= 2; reach++) {
            for (int column = -west; column <= east; column++) {
                cell.set(pos.getX() + dx * reach + column, pos.getY(), pos.getZ() + dz * reach);
                if (!volume.contains(cell)) return reach - 1;
            }
        }
        return 2;
    }

    private static void drawWisp(VertexConsumer buffer, PoseStack.Pose matrix, Wisp wisp, float time, float direction, float height,
        boolean ceiling, int color, int alpha) {
        float clock = time * wisp.speed() + wisp.phase();
        float age = clock - Mth.floor(clock);
        float fadeIn = Mth.clamp(age * 6, 0, 1);
        float fadeOut = 1 - age;
        int opacity = (int) (alpha * fadeIn * fadeOut);
        if (opacity == 0) return;

        float angle = direction + .76f * Mth.sin(time * .024f + .21f * wisp.x() + .25f * wisp.z()) + .21f * wisp.bend();
        float dx = Mth.cos(angle), dz = Mth.sin(angle);
        float travel = .56f * age - .28f;
        float turn = .09f * Mth.sin(Mth.PI * age) * wisp.bend();
        float x = wisp.x() + (dx * travel - dz * turn) * wisp.width();
        float z = wisp.z() + (dz * travel + dx * turn) * wisp.depth();

        // Center offset and quad corners fit in the occupied rectangle, even as the heading turns.
        float along = Math.min(.11f + .24f * age, .40f - Math.abs(travel));
        float across = .11f + .08f * age;
        float alongX = dx * along * wisp.width(), alongZ = dz * along * wisp.depth();
        float acrossX = -dz * across * wisp.width(), acrossZ = dx * across * wisp.depth();
        float lift = height * (.028f + .27f * age);
        float thickness = height * (.34f + .24f * age);
        float base = wisp.y() + (ceiling ? height - lift : lift);
        float tip = base + (ceiling ? -thickness : thickness);
        draw(buffer, matrix, x, z, base, tip, alongX, alongZ, acrossX, acrossZ, color, opacity);
        draw(buffer, matrix, x, z, base, Mth.lerp(.74f, base, tip), acrossX, acrossZ, -alongX, -alongZ, color, opacity * 2 / 3);
    }

    private static void draw(VertexConsumer buffer, PoseStack.Pose matrix, float x, float z, float base, float tip,
        float alongX, float alongZ, float acrossX, float acrossZ, int color, int alpha) {
        vertex(buffer, matrix, x - alongX - acrossX, base, z - alongZ - acrossZ, 0, 1, color, alpha);
        vertex(buffer, matrix, x + alongX - acrossX, base, z + alongZ - acrossZ, 1, 1, color, alpha);
        vertex(buffer, matrix, x + alongX + acrossX, tip, z + alongZ + acrossZ, 1, 0, color, alpha);
        vertex(buffer, matrix, x - alongX + acrossX, tip, z - alongZ + acrossZ, 0, 0, color, alpha);
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
