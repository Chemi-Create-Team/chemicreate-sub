package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.block.entity.FlowRegulatorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Turns Create's valve pointer through the full circle as the flow setting goes from closed to fully open. */
public class FlowRegulatorRenderer extends SafeBlockEntityRenderer<FlowRegulatorBlockEntity> {
    public FlowRegulatorRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(FlowRegulatorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        Direction plate = FlowRegulatorBlockEntity.plate(state);
        float angle = be.pointerDegrees();
        CachedBuffers.partial(AllPartialModels.FLUID_VALVE_POINTER, state).center()
            .rotateYDegrees(AngleHelper.horizontalAngle(plate))
            .rotateXDegrees(plate == Direction.UP ? 0 : plate == Direction.DOWN ? 180 : 90)
            .rotateYDegrees(angle + (state.getValue(BlockStateProperties.AXIS) == Direction.Axis.X ? 90 : 0))
            .uncenter().light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
