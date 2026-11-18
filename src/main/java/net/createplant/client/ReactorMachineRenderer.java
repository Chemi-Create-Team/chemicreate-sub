package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ReactorMachineBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public class ReactorMachineRenderer extends KineticBlockEntityRenderer<ReactorMachineBlockEntity> {
    private static final PartialModel NEEDLE=PartialModel.of(CreatePlant.location("block/reactor/needle"));
    public static void init() { }
    public ReactorMachineRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @Override protected void renderSafe(ReactorMachineBlockEntity be,float partialTicks,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (be.horizontal()) return;
        renderRotatingKineticBlock(be,shaft(getRotationAxisOf(be)),pose,buffers.getBuffer(RenderType.solid()),light);
        pose.pushPose();
        if (getRotationAxisOf(be)==Direction.Axis.X) { pose.translate(.5,0,.5);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));pose.translate(-.5,0,-.5); }
        pose.translate(.5,12/16f,1/16f);
        CachedBuffers.partial(NEEDLE,be.getBlockState()).rotate((be.progress()/be.cycleTicks()*240-120)*(float)Math.PI/180,Direction.SOUTH).light(light).renderInto(pose,buffers.getBuffer(RenderType.solid()));
        pose.popPose();
    }
}
