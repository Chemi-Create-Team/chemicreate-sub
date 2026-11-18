package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createplant.guide.FusionGuide;
import net.createplant.guide.GuideSelector;
import net.createplant.guide.ReactorGuide;
import net.createplant.guide.StructureGuide;
import net.createplant.guide.TankGuide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the blocks still missing from an unfinished multiblock as translucent ghosts. Client only: nothing is placed, saved or sent.
 * Block entities near the player are scanned twice a second; frames only draw the cached plans.
 */
public final class StructureGuideRenderer {
    private static final List<StructureGuide> GUIDES = List.of(new ReactorGuide(),
        new TankGuide(), new FusionGuide());
    private static final int CHUNK_RADIUS = 3;
    private static final double RANGE = 64;
    private static final float OPACITY = .4f;
    private static final Map<BlockPos, GuideSelector.Plan> PLANS = new HashMap<>();

    private StructureGuideRenderer() { }

    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null) { PLANS.clear();return; }
        if (level.getGameTime() % 10 != 0) return;
        List<BlockEntity> area = new ArrayList<>();
        int cx = mc.player.chunkPosition().x, cz = mc.player.chunkPosition().z;
        for (int x = cx - CHUNK_RADIUS; x <= cx + CHUNK_RADIUS; x++) for (int z = cz - CHUNK_RADIUS; z <= cz + CHUNK_RADIUS; z++) {
            var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null) area.addAll(chunk.getBlockEntities().values());
        }
        BlockPos player = mc.player.blockPosition();
        Map<BlockPos, GuideSelector.Plan> found = new HashMap<>();
        for (StructureGuide guide : GUIDES) {
            // Nearest anchors first, each guide limited to a few structures.
            List<BlockPos> anchors = new ArrayList<>(guide.anchors(level, mc.player, area));
            anchors.removeIf(anchor -> anchor.distSqr(player) > RANGE * RANGE || area.stream().anyMatch(be ->
                be instanceof net.createplant.block.entity.FacilityConstructorBlockEntity facility && facility.getRenderBoundingBox().contains(anchor.getCenter())));
            anchors.sort(Comparator.comparingDouble(anchor -> anchor.distSqr(player)));
            for (BlockPos anchor : anchors.subList(0, Math.min(guide.limit(), anchors.size()))) {
                var plan = GuideSelector.choose(level, guide.layouts(level, anchor));
                if (plan != null) found.put(anchor, plan);
            }
        }
        PLANS.clear();
        PLANS.putAll(found);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || PLANS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.translucentMovingBlock();
        var consumer = new Faded(buffers.getBuffer(type), OPACITY);
        PoseStack pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        for (var plan : PLANS.values()) for (var cell : plan.missing()) {
            BlockPos pos = cell.pos();
            if (pos.distSqr(BlockPos.containing(camera)) > RANGE * RANGE) continue;
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            var dispatcher = mc.getBlockRenderer();
            dispatcher.getModelRenderer().renderModel(pose.last(), consumer, cell.ghost(), dispatcher.getBlockModel(cell.ghost()), 1, 1, 1, LevelRenderer.getLightColor(level, pos), OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    /** Scales the alpha of everything drawn through it. */
    private record Faded(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z);return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, Math.round(a * alpha));return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v);return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v);return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v);return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z);return this; }
    }
}
