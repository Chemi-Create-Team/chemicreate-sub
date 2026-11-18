package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createplant.block.entity.FlareTipBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;

/** Overlapping sheets of vanilla animated fire keep its block-scale pixel density along the whole plume. */
public class FlareTipRenderer extends SafeBlockEntityRenderer<FlareTipBlockEntity> {
    public FlareTipRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(FlareTipBlockEntity be,float partialTicks,PoseStack poses,MultiBufferSource buffer,int light,int overlay) {
        float height=be.flameHeight(partialTicks);
        double time=AnimationTickHolder.getRenderTime(be.getLevel());
        var sprite=Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
            .apply(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/fire_0"));
        var vertices=buffer.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        int segments=(int)Math.ceil(height*2.5);
        for (int i=0;i<segments;i++) {
            float y=i*.4f;
            float end=Math.min(height,y+1);
            double lower=y/Math.max(1,height),upper=end/Math.max(1,height);
            double lowerWidth=width(lower),upperWidth=width(upper);
            for (int plane=0;plane<4;plane++) {
                double angle=plane*Math.PI/4,dx=Math.cos(angle),dz=Math.sin(angle);
                double x0=.5+.015*y+Math.sin(time*.09+y*.7)*.1*lower,z0=.5+Math.cos(time*.07+y*.6)*.12*lower;
                double x1=.5+.015*end+Math.sin(time*.09+end*.7)*.1*upper,z1=.5+Math.cos(time*.07+end*.6)*.12*upper;
                vertex(vertices,poses,x0-dx*lowerWidth/2,1+y,z0-dz*lowerWidth/2,sprite.getU0(),sprite.getV1(),overlay);
                vertex(vertices,poses,x0+dx*lowerWidth/2,1+y,z0+dz*lowerWidth/2,sprite.getU1(),sprite.getV1(),overlay);
                vertex(vertices,poses,x1+dx*upperWidth/2,1+end,z1+dz*upperWidth/2,sprite.getU1(),sprite.getV0(),overlay);
                vertex(vertices,poses,x1-dx*upperWidth/2,1+end,z1-dz*upperWidth/2,sprite.getU0(),sprite.getV0(),overlay);
            }
        }
        if (be.pilot()) {
            poses.pushPose();poses.translate(.02,1.03,.40);poses.scale(.2f,.35f,.2f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.FIRE.defaultBlockState(),poses,buffer,LightTexture.FULL_BRIGHT,overlay);poses.popPose();
        }
    }
    private static double width(double fraction) { return (.6+1.1*Math.sin(Math.PI*fraction))*(1-.5*fraction); }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vertices,PoseStack poses,double x,double y,double z,float u,float v,int overlay) {
        vertices.addVertex(poses.last(),(float)x,(float)y,(float)z).setColor(255,255,255,255).setUv(u,v).setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(poses.last(),0,1,0);
    }
    @Override public int getViewDistance() { return 256; }
}
