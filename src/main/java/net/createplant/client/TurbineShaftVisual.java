package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;

import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.joml.Quaternionf;

final class TurbineShaftVisual {
    private TurbineShaftVisual() {}

    static void render(
            Direction.Axis axis, float angle, PoseStack pose, VertexConsumer buffer, int light) {
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(axis));
        pose.mulPose(new Quaternionf().rotateX(angle));
        pose.translate(-.5, -.5, -.5);
        CachedBuffers.block(
                        AllBlocks.SHAFT
                                .getDefaultState()
                                .setValue(BlockStateProperties.AXIS, Direction.Axis.X))
                .light(light)
                .renderInto(pose, buffer);
        pose.popPose();
    }
}
