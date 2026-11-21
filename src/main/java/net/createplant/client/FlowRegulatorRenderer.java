package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createplant.block.entity.FlowRegulatorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The pipe flow meter's needle uses its full sweep to read the regulator's setting. */
public class FlowRegulatorRenderer extends SafeBlockEntityRenderer<FlowRegulatorBlockEntity> {
    private static final float FACE_INSET=.5f/16;
    public FlowRegulatorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(FlowRegulatorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        Direction plate = FlowRegulatorBlockEntity.plate(state);
        Direction.Axis axis=state.getValue(BlockStateProperties.AXIS);
        float setting=be.gaugeProgress();
        var vertices=buffer.getBuffer(RenderType.solid());
        for (Direction face:Direction.Plane.HORIZONTAL) {
            if (face==plate || face.getAxis()==axis || !Block.shouldRenderFace(state,be.getLevel(),be.getBlockPos(),face,be.getBlockPos().relative(face))) continue;
            PipeGaugeRenderer.renderDial(state,PipeGaugeRenderer.faceAngle(face),setting,FACE_INSET,ms,vertices,light);
        }
    }
}
