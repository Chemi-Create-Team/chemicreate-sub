package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.CreatePlant;
import net.createplant.block.GasHolderGeometry;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

/** White tank bells nest inside the fixed native tank; every panel keeps 16 texels per metre. */
public class GasHolderRenderer extends SafeBlockEntityRenderer<GasHolderBlockEntity> {
    private record Model(float[] foundation,List<float[]> shells,List<float[]> rims) { }
    private final Map<GasHolderGeometry,Model> meshes=new HashMap<>();
    public static void init() { }
    public GasHolderRenderer(BlockEntityRendererProvider.Context context) { }
    @Override protected void renderSafe(GasHolderBlockEntity be,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlay) {
        if (!be.formed() || be.geometry()==null) return;
        var geometry=be.geometry();var mesh=meshes.computeIfAbsent(geometry,GasHolderRenderer::bakeModel);
        var atlas=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var plate=atlas.apply(CreatePlant.location("block/gas_holder/plate"));
        var rim=atlas.apply(CreatePlant.location("block/gas_holder/rim"));
        var metal=buffers.getBuffer(RenderType.solid());
        double lift=be.renderedLift(partial),height=geometry.height(),width=geometry.width();
        draw(metal,poses,mesh.foundation(),rim,light,overlay);
        for (int stage=0;stage<3;stage++) {
            poses.pushPose();poses.translate(0,.125+geometry.offset(lift,stage),0);
            draw(metal,poses,mesh.shells().get(stage),plate,light,overlay);
            draw(metal,poses,mesh.rims().get(stage),rim,light,overlay);
            poses.popPose();
        }
        var water=FluidRenderHelper.getFluidBuilder(buffers);var seal=new FluidStack(Fluids.WATER,1000);
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(seal,.0625f,.125f,.0625f,(float)(width-.0625),(float)(height-.25),(float)(width-.0625),water,poses,light,false,true);
        for (int stage=0;stage<2;stage++) {
            double inset=geometry.inset(stage)+.03125,y=.125+height+geometry.offset(lift,stage)-.08;
            double inner=geometry.inset(stage+1);
            waterStrip(seal,inset,y,inset,width-inset,inner,water,poses,light);
            waterStrip(seal,inset,y,width-inner,width-inset,width-inset,water,poses,light);
            waterStrip(seal,inset,y,inner,inner,width-inner,water,poses,light);
            waterStrip(seal,width-inner,y,inner,width-inset,width-inner,water,poses,light);
        }
    }
    private static void waterStrip(FluidStack fluid,double x0,double y,double z0,double x1,double z1,VertexConsumer vc,PoseStack poses,int light) {
        if (x1>x0 && z1>z0) NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid,(float)x0,(float)y,(float)z0,(float)x1,(float)(y+.0125),(float)z1,vc,poses,light,false,true);
    }
    private static Model bakeModel(GasHolderGeometry geometry) {
        var shells=new ArrayList<float[]>();var rims=new ArrayList<float[]>();
        for (int stage=0;stage<3;stage++) {
            shells.add(bake(geometry.shell(stage)));double inset=geometry.inset(stage);
            rims.add(bake(ring(inset-.03125,geometry.height()-.125,geometry.width()-inset+.03125,geometry.height(),.0625)));
        }
        return new Model(bake(ring(.0625,geometry.height()-.125,geometry.width()-.0625,geometry.height()+.0625,.0625)),List.copyOf(shells),List.copyOf(rims));
    }
    private static List<AABB> ring(double a,double y0,double b,double y1,double t) {
        return List.of(new AABB(a,y0,a,b,y1,a+t),new AABB(a,y0,b-t,b,y1,b),new AABB(a,y0,a+t,a+t,y1,b-t),new AABB(b-t,y0,a+t,b,y1,b-t));
    }
    private static float[] bake(List<AABB> boxes) {
        var vertices=new ArrayList<Float>();
        for (var box:boxes) for (var face:Direction.values()) {
            int normal=face.getAxis().ordinal(),u=normal==0 ? 2 : 0,v=normal==1 ? 2 : 1;
            double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ};
            boolean reverse=face==Direction.UP || face==Direction.NORTH || face==Direction.EAST;
            float shade=face==Direction.UP ? 1 : face==Direction.DOWN ? .6f : face.getAxis()==Direction.Axis.X ? .8f : .9f;
            for (double a=min[u];a<max[u]-1e-6;) {
                double endA=Math.min(max[u],Math.floor(a+1e-6)+1);
                for (double c=min[v];c<max[v]-1e-6;) {
                    double endC=Math.min(max[v],Math.floor(c+1e-6)+1);
                    double[][] points=reverse ? new double[][]{{a,endC},{endA,endC},{endA,c},{a,c}} : new double[][]{{a,c},{endA,c},{endA,endC},{a,endC}};
                    for (var point:points) {
                        double[] xyz=new double[3];xyz[normal]=face.getAxisDirection()==Direction.AxisDirection.POSITIVE ? max[normal] : min[normal];xyz[u]=point[0];xyz[v]=point[1];
                        for (double coordinate:xyz) vertices.add((float)coordinate);
                        vertices.add((float)(point[0]-Math.floor(a+1e-6)));vertices.add((float)(point[1]-Math.floor(c+1e-6)));vertices.add(shade);
                        vertices.add((float)face.getStepX());vertices.add((float)face.getStepY());vertices.add((float)face.getStepZ());
                    }
                    c=endC;
                }
                a=endA;
            }
        }
        float[] result=new float[vertices.size()];for (int i=0;i<result.length;i++) result[i]=vertices.get(i);return result;
    }
    private static void draw(VertexConsumer vc,PoseStack poses,float[] mesh,TextureAtlasSprite sprite,int light,int overlay) {
        var pose=poses.last();
        for (int i=0;i<mesh.length;i+=9) vc.addVertex(pose,mesh[i],mesh[i+1],mesh[i+2]).setColor(mesh[i+5],mesh[i+5],mesh[i+5],1)
            .setUv(sprite.getU(mesh[i+3]),sprite.getV(mesh[i+4])).setOverlay(overlay).setLight(light).setNormal(pose,mesh[i+6],mesh[i+7],mesh[i+8]);
    }
    @Override public int getViewDistance() { return 128; }
}
