package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createplant.block.entity.HoseConnectorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class HoseConnectorRenderer extends SafeBlockEntityRenderer<HoseConnectorBlockEntity> {
    public HoseConnectorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(HoseConnectorBlockEntity connector, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        HoseRenderer.draw(connector, pose, buffers, light);
    }

    @Override public AABB getRenderBoundingBox(HoseConnectorBlockEntity connector) { return HoseRenderer.bounds(connector); }
    @Override public boolean shouldRenderOffScreen(HoseConnectorBlockEntity connector) { return true; }
    @Override public int getViewDistance() { return 96; }
}
