package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.PipeGaugeBlock;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** Create's gauge head and dial on each open side across the pipe, turned as Create's GaugeRenderer turns them. */
public class PipeGaugeRenderer extends SafeBlockEntityRenderer<PipeGaugeBlockEntity> {
    private static final PartialModel THERMOMETER=PartialModel.of(CreatePlant.location("block/pipe_gauge/thermometer_head"));
    private static final PartialModel PRESSURE=PartialModel.of(CreatePlant.location("block/pipe_gauge/pressure_head"));
    private static final PartialModel FLOW=PartialModel.of(CreatePlant.location("block/pipe_gauge/flow_head"));
    private static final float DIAL_PIVOT=5.75f/16;
    public static void init() { }
    public PipeGaugeRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(PipeGaugeBlockEntity be,float partialTicks,PoseStack ms,MultiBufferSource buffer,int light,int overlay) {
        var state=be.getBlockState();
        if (!(state.getBlock() instanceof PipeGaugeBlock block)) return;
        var vb=buffer.getBuffer(RenderType.solid());
        float progress=Mth.lerp(partialTicks,be.prevDialState,be.dialState);
        for (Direction face:Direction.Plane.HORIZONTAL) {
            if (!block.shouldRenderHeadOnFace(be.getLevel(),be.getBlockPos(),state,face)) continue;
            float towards=faceAngle(face);
            renderDial(state,towards,progress,0,ms,vb,light);
            CachedBuffers.partial(switch (block.kind) { case TEMPERATURE -> THERMOMETER;case PRESSURE -> PRESSURE;case FLOW -> FLOW; },state).rotateCentered(towards,Direction.UP).light(light).renderInto(ms,vb);
        }
    }

    static float faceAngle(Direction face) {
        return (float)((-face.toYRot()-90)/180*Math.PI);
    }

    static void renderDial(BlockState state,float towards,float progress,float inset,PoseStack pose,VertexConsumer vertices,int light) {
        CachedBuffers.partial(AllPartialModels.GAUGE_DIAL,state).rotateCentered(towards,Direction.UP)
            .translate(-inset,DIAL_PIVOT,DIAL_PIVOT).rotate((float)(Math.PI/2*-progress),Direction.EAST)
            .translate(0,-DIAL_PIVOT,-DIAL_PIVOT).light(light).renderInto(pose,vertices);
    }
}
