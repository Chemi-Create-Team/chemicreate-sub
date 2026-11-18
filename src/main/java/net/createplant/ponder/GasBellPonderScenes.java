package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.entity.GasHolderBlockEntity;
import net.createplant.registry.CEFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GasBellPonderScenes {
    private static final String LANG="ponder.create_refinery_n_reactors.gas_bell.";
    private static final BlockPos TANK=new BlockPos(4,2,4);
    private GasBellPonderScenes() { }
    public static void bell(SceneBuilder builder,SceneBuildingUtil util) {
        var scene=new CreateSceneBuilder(builder);scene.title("gas_bell",PonderText.title(LANG+"title","A telescopic water-sealed gas holder"));
        scene.configureBasePlate(0,0,13);scene.scaleSceneView(.34f);scene.setSceneOffsetY(-3);scene.showBasePlate();
        PonderText.build(scene, util, 4,2,4,6,4,6, Direction.DOWN);preview(scene,0,0);
        text(scene,util,TANK,"build");text(scene,util,TANK.above(2),"seal");
        PonderText.build(scene, util, 1,1,5,4,1,5, Direction.EAST);
        scene.world().setKineticSpeed(util.select().position(2,1,5),32);text(scene,util,TANK.below(),"pipes");
        preview(scene,72000,0);text(scene,util,TANK.above(5),"first");
        preview(scene,144000,2.5);text(scene,util,TANK.above(7),"second");
        preview(scene,216000,5);text(scene,util,TANK.above(10),"full");
        preview(scene,0,7.5);text(scene,util,TANK.above(3),"empty");
        text(scene,util,TANK,"contaminate");text(scene,util,TANK,"reaction");
        scene.overlay().showControls(util.vector().centerOf(TANK),Pointing.RIGHT,100).withItem(AllItems.WRENCH.asStack()).whileSneaking().rightClick();
        text(scene,util,TANK,"purge");scene.markAsFinished();
    }
    private static void preview(CreateSceneBuilder scene,int amount,double lift) {
        scene.world().modifyBlockEntity(TANK,GasHolderBlockEntity.class,be->be.preview(3,3,amount==0 ? FluidStack.EMPTY : new FluidStack(CEFluids.NITROGEN_GAS.get(),amount),lift));
    }
    private static void text(CreateSceneBuilder scene,SceneBuildingUtil util,BlockPos pos,String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG+key, 140);
    }
}
