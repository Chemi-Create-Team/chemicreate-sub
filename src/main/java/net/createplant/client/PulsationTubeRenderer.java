package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.PulsationTubeBlockEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** The existing Create pressure dial indicates the stored gas's pressure grade. */
public class PulsationTubeRenderer extends SafeBlockEntityRenderer<PulsationTubeBlockEntity> {
    private static final PartialModel HEAD=PartialModel.of(CreatePlant.location("block/pipe_gauge/pressure_head"));
    public static void init(){ }
    public PulsationTubeRenderer(BlockEntityRendererProvider.Context context){ }
    @Override protected void renderSafe(PulsationTubeBlockEntity be,float partialTicks,PoseStack poses,MultiBufferSource buffer,int light,int overlay){
        if(!be.isController())return;
        var face=be.getMainConnectionAxis()==Direction.Axis.Z ? Direction.WEST : Direction.NORTH;
        if(!be.getLevel().getBlockState(be.getBlockPos().relative(face)).canBeReplaced())return;
        float rotation=(float)((-face.toYRot()-90)/180*Math.PI),pivot=5.75f/16;
        float progress=be.isVirtual() ? be.pressureDial() : Mth.lerp(partialTicks,be.previousDial,be.dial);
        var vertices=buffer.getBuffer(RenderType.solid());var state=be.getBlockState();
        CachedBuffers.partial(HEAD,state).rotateCentered(rotation,Direction.UP).light(light).renderInto(poses,vertices);
        CachedBuffers.partial(AllPartialModels.GAUGE_DIAL,state).rotateCentered(rotation,Direction.UP).translate(0,pivot,pivot).rotate((float)(Math.PI/2*-progress),Direction.EAST).translate(0,-pivot,-pivot).light(light).renderInto(poses,vertices);
    }
}
