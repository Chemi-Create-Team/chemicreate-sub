package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.block.entity.PulsationTubeBlockEntity;
import net.createplant.registry.CEFluids;
import net.createmod.ponder.api.scene.*;
import net.minecraft.core.*;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class PulsationTubePonderScenes {
    private PulsationTubePonderScenes(){ }
    public static void tube(SceneBuilder builder,SceneBuildingUtil util){
        var scene=new CreateSceneBuilder(builder);String lang="ponder.create_refinery_n_reactors.pulsation_tube.";
        scene.title("pulsation_tube",PonderText.title(lang+"title","Pulsation damping tubes"));scene.configureBasePlate(0,0,11);scene.scaleSceneView(.7f);scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(1,1,4,9,3,4),Direction.DOWN);
        scene.world().setKineticSpeed(util.select().fromTo(1,1,4,9,3,4),64);
        PonderText.caption(scene,util.vector().of(4,3,4),lang+"flow",140);
        scene.world().modifyBlockEntity(new BlockPos(3,2,4),PulsationTubeBlockEntity.class,be->be.getTankInventory().fill(new FluidStack(CEFluids.NITROGEN_GAS.get(),24000),EXECUTE));
        PonderText.caption(scene,util.vector().of(3,3,4),lang+"buffer",150);
        scene.world().showSection(util.select().fromTo(4,1,7,7,5,10),Direction.DOWN);
        PonderText.caption(scene,util.vector().of(4,4,7),lang+"axes",150);
        scene.overlay().showControls(util.vector().of(7,3,7),net.createmod.catnip.math.Pointing.DOWN,80).withItem(AllItems.WRENCH.asStack());
        PonderText.caption(scene,util.vector().of(7,3,7),lang+"wrench",130);
        scene.world().showSection(util.select().position(1,1,1),Direction.DOWN);
        PonderText.caption(scene,util.vector().of(1,2,1),lang+"liquid",150);scene.markAsFinished();
    }
}
