package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.FlareTipBlock;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FlarePonderScenes {
    private static final String LANG="ponder.create_refinery_n_reactors.flare_stack.";
    private static final BlockPos GAUGE=new BlockPos(2,1,4),VALVE=new BlockPos(3,1,5),TIP=new BlockPos(8,10,6);
    private FlarePonderScenes() { }
    public static void flare(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);
        scene.title("flare_stack",PonderText.title(LANG+"title","Disposing of excess gas"));
        scene.configureBasePlate(0,0,11);scene.scaleSceneView(.28f);scene.setSceneOffsetY(-1);scene.showBasePlate();
        PonderText.build(scene, util, 1,1,4,5,1,4, Direction.DOWN);
        scene.world().modifyBlockEntity(GAUGE,PipeGaugeBlockEntity.class,g->{g.threshold.setValue(0);g.preview(12e5);});
        text(scene,util,GAUGE,"pressure");
        PonderText.build(scene, util, 3,1,5,8,1,6, Direction.DOWN);
        text(scene,util,VALVE,"bypass");
        PonderText.build(scene, util, 2,1,1,6,1,3, Direction.DOWN);
        scene.overlay().showControls(util.vector().centerOf(GAUGE),Pointing.RIGHT,100).rightClick().withItem(AllItems.WRENCH.asStack());
        text(scene,util,new BlockPos(2,1,3),"circuit");
        PonderText.build(scene, util, 2,1,5,2,2,5, Direction.DOWN);
        text(scene,util,new BlockPos(2,2,5),"links");
        PonderText.build(scene, util, 7,1,5,9,10,8, Direction.DOWN);
        text(scene,util,TIP,"chimney");
        scene.overlay().showControls(util.vector().topOf(TIP),Pointing.DOWN,100).rightClick().withItem(new ItemStack(Items.FLINT_AND_STEEL));
        scene.world().modifyBlock(TIP,state->state.setValue(FlareTipBlock.PILOT,true),false);
        text(scene,util,TIP,"pilot");
        scene.world().modifyBlockEntity(GAUGE,PipeGaugeBlockEntity.class,g->g.preview(14e5));
        for (var pos:new BlockPos[]{new BlockPos(2,1,1),new BlockPos(2,2,5)}) scene.world().modifyBlock(pos,state->state.setValue(RedstoneLinkBlock.POWERED,true),false);
        scene.world().modifyBlock(VALVE,state->state.setValue(com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock.ENABLED,true),false);
        scene.world().modifyBlockEntity(TIP,net.createplant.block.entity.FlareTipBlockEntity.class,tip->tip.preview(6,900));
        text(scene,util,TIP,"burn");
        scene.world().modifyBlockEntity(TIP,net.createplant.block.entity.FlareTipBlockEntity.class,tip->tip.preview(0,0));
        scene.world().modifyBlockEntity(GAUGE,PipeGaugeBlockEntity.class,g->g.preview(12e5));
        text(scene,util,TIP,"hazards");scene.markAsFinished();
    }
    private static void text(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos pos,String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG+key, 140);
    }
}
