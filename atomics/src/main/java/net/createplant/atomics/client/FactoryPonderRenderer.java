package net.createplant.atomics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createplant.atomics.block.entity.ContinuousFactoryBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Ponder has a virtual world without tracked entities. It uses the same plans and meshes as the actual bodies. */
public final class FactoryPonderRenderer extends SafeBlockEntityRenderer<ContinuousFactoryBlockEntity> {
    public FactoryPonderRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(ContinuousFactoryBlockEntity be, float partial, PoseStack stack, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isVirtual()) return;
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        be.previewParts(partial, (id, geometry, pose) -> FactoryBodyRenderer.draw(geometry, pose, origin, stack, buffers, light, true, false));
    }

    @Override public boolean shouldRenderOffScreen(ContinuousFactoryBlockEntity be) { return be.isVirtual(); }
}
