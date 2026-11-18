package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createplant.block.entity.AmmoniaSeparatorBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Only the liquid in the model's sight glass moves; one controller draws all its layers. */
public class AmmoniaSeparatorRenderer extends SafeBlockEntityRenderer<AmmoniaSeparatorBlockEntity> {
    public AmmoniaSeparatorRenderer(BlockEntityRendererProvider.Context context) { }
    @Override protected void renderSafe(AmmoniaSeparatorBlockEntity be,float partialTicks,PoseStack poses,MultiBufferSource buffer,int light,int overlay){
        if(!be.isController())return;
        var liquid=be.gaugeLiquid();if(liquid.isEmpty())return;
        float surface=1/16f+(be.getHeight()-2/16f)*be.liquidFraction();
        poses.pushPose();poses.translate(.5,0,.5);poses.mulPose(Axis.YP.rotationDegrees(180-be.front().toYRot()));poses.translate(-.5,0,-.5);
        for(int y=0;y<be.getHeight();y++){
            float bottom=y+1/16f,top=Math.min(y+15/16f,surface);if(top<=bottom)break;
            int segmentLight=LevelRenderer.getLightColor(be.getLevel(),be.getBlockPos().above(y).relative(be.front()));
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(liquid,7.125f/16,bottom,.125f/16,8.875f/16,top,.875f/16,buffer,poses,segmentLight,false,true);
        }
        poses.popPose();
    }
    @Override public boolean shouldRenderOffScreen(AmmoniaSeparatorBlockEntity be){return be.isController();}
}
