package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.ElectricPumpBlock;
import net.createplant.block.entity.ElectricPumpBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class ElectricPumpRenderer extends SafeBlockEntityRenderer<ElectricPumpBlockEntity> {
    private static final PartialModel FAN =
            PartialModel.of(CreatePlant.location("block/electric_utilities/pump_fan"));

    public static void init() {}

    public ElectricPumpRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(
            ElectricPumpBlockEntity be,
            float partialTicks,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var facing = be.getBlockState().getValue(ElectricPumpBlock.FACING);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        switch (facing) {
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            default -> {}
        }
        pose.mulPose(
                Axis.XP.rotationDegrees(
                        AnimationTickHolder.getRenderTime(be.getLevel()) * be.getSpeed() * .3f));
        pose.translate(-.5, -.5, -.5);
        CachedBuffers.partial(FAN, be.getBlockState())
                .light(light)
                .renderInto(pose, buffers.getBuffer(RenderType.solid()));
        pose.popPose();
    }
}
