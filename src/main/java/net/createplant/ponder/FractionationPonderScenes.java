package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.block.FractionatingColumnBlock;
import net.createplant.block.entity.FractionatingColumnBlockEntity;
import net.createplant.chemistry.DistillationProfile;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public final class FractionationPonderScenes {
    private static final BlockPos OWNER=new BlockPos(8,1,13);
    private FractionationPonderScenes() { }
    public static void column(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=begin(builder,"fractionating_column",5);
        PonderText.build(scene,util,8,1,13,9,5,14,Direction.DOWN);
        text(scene,util,OWNER,"fractionating_column.base");
        text(scene,util,OWNER.above(3),"fractionating_column.build");
        input(scene,util,5);text(scene,util,OWNER.east().above(2),"fractionating_column.feed");
        preview(scene,5,"distill_crude",false);
        output(scene,util,4,0);output(scene,util,3,1);
        text(scene,util,OWNER.above(4),"fractionating_column.light");
        output(scene,util,2,2);output(scene,util,1,3);
        text(scene,util,OWNER.above(2),"fractionating_column.middle");
        output(scene,util,0,4);preview(scene,5,"distill_crude",true);
        text(scene,util,OWNER,"fractionating_column.heavy");
        text(scene,util,OWNER,"fractionating_column.empty");
        goggles(scene,util,OWNER.above(2));text(scene,util,OWNER.above(2),"fractionating_column.observe");scene.markAsFinished();
    }
    public static void binary(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=begin(builder,"fractionating_column_binary",2);
        PonderText.build(scene,util,8,1,13,9,2,14,Direction.DOWN);
        text(scene,util,OWNER,"fractionating_column_binary.build");
        input(scene,util,2);text(scene,util,OWNER.east().above(),"fractionating_column_binary.feed");
        preview(scene,2,"separate_nitric_oxide",false);output(scene,util,1,0);output(scene,util,0,1);
        preview(scene,2,"separate_nitric_oxide",true);
        text(scene,util,OWNER.above(),"fractionating_column_binary.outputs");
        text(scene,util,OWNER,"fractionating_column.empty");scene.markAsFinished();
    }

    public static void synthetic(SceneBuilder builder, SceneBuildingUtil util) {
        int height = 5;
        String recipe = "distill_synthetic_crude";
        var scene = begin(builder, "synthetic_fractionation", height);
        PonderText.build(scene, util, 8, 1, 13, 9, height, 14, Direction.DOWN);
        input(scene, util, height);
        text(scene, util, OWNER.east().above(2), "synthetic_fractionation.feed");
        preview(scene, height, recipe, false);
        var fraction = DistillationProfile.fraction(recipe);
        for (int slot = 1; slot <= fraction.outputs().size(); slot++) {
            int layer = DistillationProfile.fractionLayer(slot, height, fraction.outputs().size());
            output(scene, util, layer, height - 1 - layer);
        }
        preview(scene, height, recipe, true);
        text(scene, util, OWNER.above(4), "synthetic_fractionation.products");
        text(scene, util, OWNER, "synthetic_fractionation.uses");
        scene.markAsFinished();
    }
    public static void air(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=begin(builder,"fractionating_column_air",14);
        PonderText.build(scene,util,8,1,13,9,14,14,Direction.DOWN);
        text(scene,util,OWNER.above(7),"fractionating_column_air.build");
        input(scene,util,14);text(scene,util,OWNER.east().above(2),"fractionating_column_air.feed");
        preview(scene,14,"air",false);output(scene,util,13,0);output(scene,util,9,1);output(scene,util,0,2);
        preview(scene,14,"air",true);text(scene,util,OWNER.above(9),"fractionating_column_air.outputs");
        text(scene,util,OWNER.above(13),"fractionating_column.air_ports");
        goggles(scene,util,OWNER.above(7));text(scene,util,OWNER,"fractionating_column.empty");scene.markAsFinished();
    }
    private static CreateSceneBuilder begin(SceneBuilder builder,String id,int height) {
        var scene=new CreateSceneBuilder(builder);scene.title(id,PonderText.title("ponder.create_refinery_n_reactors."+id+".title",id));
        scene.configureBasePlate(0,0,17);scene.scaleSceneView(height==14 ? .35f : .52f);scene.setSceneOffsetY(height==14 ? -5 : -1);scene.showBasePlate();return scene;
    }
    private static void input(CreateSceneBuilder scene,SceneBuildingUtil util,int height) {
        int y=1+DistillationProfile.feedLayer(height);
        PonderText.build(scene,util,10,y-1,13,13,y,13,Direction.WEST);
        scene.world().setKineticSpeed(util.select().fromTo(10,y-1,13,13,y,13),64);
    }
    private static void output(CreateSceneBuilder scene,SceneBuildingUtil util,int layer,int index) {
        int y=layer+1;
        PonderText.build(scene,util,8,y,9-2*index,9,y,12,Direction.SOUTH);
        scene.world().setKineticSpeed(util.select().fromTo(8,y,9-2*index,9,y,12),64);
    }
    private static void preview(CreateSceneBuilder scene,int height,String recipe,boolean stocked) {
        var fraction=DistillationProfile.fraction(recipe);int cuts=fraction==null ? 0 : fraction.outputs().size();
        for(int y=0;y<height;y++) {
            int slot=fraction==null ? DistillationProfile.outlet(y) : DistillationProfile.fractionSlot(y,height,cuts);
            for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
                int cut=x==0 && z==0 ? slot : 0;
                scene.world().modifyBlock(OWNER.offset(x,y,z),state->state.setValue(FractionatingColumnBlock.CUT,cut),false);
            }
        }
        scene.world().modifyBlockEntity(OWNER,FractionatingColumnBlockEntity.class,be->{
            var lookup=be.getLevel().registryAccess();var tag=be.writeClient(new CompoundTag(),lookup);
            tag.putString("ActiveRecipe",recipe);tag.putBoolean("Valid",true);tag.putInt("Width",2);tag.putInt("Height",height);tag.putInt("Capacity",height*4000);
            int[] stocks=new int[6];
            if(stocked) {
                if(fraction==null){stocks[1]=840;stocks[2]=40;stocks[3]=3120;}
                else for(int i=0;i<cuts;i++)stocks[i+1]=4*fraction.outputs().get(i).getAmount();
            }
            tag.putIntArray("Stocks",stocks);tag.putString("Status",stocked ? "running" : "feed");be.readClient(tag,lookup);
        });
    }
    private static void goggles(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos target) {
        scene.overlay().showControls(util.vector().centerOf(target),Pointing.RIGHT,80).withItem(AllItems.GOGGLES.asStack());
    }
    private static void text(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos target,String key) {
        PonderText.caption(scene,util.vector().centerOf(target),"ponder.create_refinery_n_reactors."+key,130);
    }
}
