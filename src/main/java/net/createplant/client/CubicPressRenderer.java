package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.CubicPressBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class CubicPressRenderer extends SafeBlockEntityRenderer<CubicPressBlockEntity> {
    private static final PartialModel ANVIL = PartialModel.of(CreatePlant.location("block/cubic_press/anvil"));
    private static final PartialModel SAMPLE = PartialModel.of(CreatePlant.location("block/cubic_press/sample"));
    public static void init() { }
    public CubicPressRenderer(BlockEntityRendererProvider.Context context) { }
    @Override protected void renderSafe(CubicPressBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var target = buffers.getBuffer(RenderType.solid());
        double phase = be.animationProgress(partialTicks) * Math.PI / 20;
        double travel = be.duration() > 0 ? (1 - Math.cos(phase)) * .5 / 16 : 0;
        for (Direction face : Direction.values()) {
            pose.pushPose();pose.translate(.5, .5, .5);
            switch (face) {
                case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                default -> { }
            }
            pose.translate(-.5, -.5, -.5 + travel);
            CachedBuffers.partial(ANVIL, be.getBlockState()).light(light).renderInto(pose, target);pose.popPose();
        }
        if (be.running()) CachedBuffers.partial(SAMPLE, be.getBlockState()).light(0xf000f0).renderInto(pose, target);
    }
}
