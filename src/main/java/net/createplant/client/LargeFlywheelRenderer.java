package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createplant.block.entity.LargeFlywheelBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class LargeFlywheelRenderer extends KineticBlockEntityRenderer<LargeFlywheelBlockEntity> {
    public LargeFlywheelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LargeFlywheelBlockEntity be, float partialTicks, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction.Axis axis = getRotationAxisOf(be);
        VertexConsumer solid = buffers.getBuffer(RenderType.solid());

        float angle = AngleHelper.rad(be.visualAngle(partialTicks));

        SuperByteBuffer shaft = CachedBuffers.block(shaft(axis));
        kineticRotationTransform(shaft, be, axis, angle, light);
        shaft.renderInto(pose, solid);

        SuperByteBuffer wheel = CachedBuffers.block(state);
        kineticRotationTransform(wheel, be, axis, angle, light);
        wheel.renderInto(pose, solid);
    }

    @Override
    public boolean shouldRenderOffScreen(LargeFlywheelBlockEntity blockEntity) {
        return true;
    }
}
