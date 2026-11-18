package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.entity.HighPressureTurbineEndBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

public class HighPressureTurbineEndRenderer
        implements BlockEntityRenderer<HighPressureTurbineEndBlockEntity> {
    public HighPressureTurbineEndRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            HighPressureTurbineEndBlockEntity be,
            float partialTick,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        renderPonderEnd(be.getBlockState(), pose, buffers, light, be.getSpeed());
    }

    public static void renderPonderEnd(
            BlockState state, PoseStack pose, MultiBufferSource buffers, int light, float speed) {
        if (HighPressureTurbineEndBlock.ringOffsetFromState(
                                state.getValue(HighPressureTurbineEndBlock.RING_X))
                        == 0
                && HighPressureTurbineEndBlock.ringOffsetFromState(
                                state.getValue(HighPressureTurbineEndBlock.RING_Y))
                        == 0) {
            float time =
                    Minecraft.getInstance().level == null
                            ? 0
                            : net.createmod.catnip.animation.AnimationTickHolder.getRenderTime(
                                    Minecraft.getInstance().level);
            TurbineShaftVisual.render(
                    state.getValue(HighPressureTurbineEndBlock.AXIS),
                    (time * speed * 3f % 360) * ((float) Math.PI / 180),
                    pose,
                    buffers.getBuffer(RenderType.solid()),
                    light);
        }
    }
}
