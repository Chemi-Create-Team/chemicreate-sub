package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.registry.CEReactor;
import net.createplant.registry.CEChlorAlkali;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The absorption tower in both sizes: gas in and acid out at the bottom, water in and spent gas out at the top. */
public final class AbsorptionTowerPonderScenes {
    private static final String LARGE = "ponder.create_refinery_n_reactors.large_absorption_tower.";

    private AbsorptionTowerPonderScenes() { }

    /** Four 3 × 3 octagonal storeys, stacked by clicking the deck. */
    public static void large(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("large_absorption_tower", PonderText.title(LARGE + "title", "Large absorption tower"));
        scene.configureBasePlate(0, 0, 11);scene.scaleSceneView(.65f);scene.showBasePlate();
        BlockPos bottom = util.grid().at(5, 1, 5), top = bottom.above(3);
        PonderText.build(scene, util, 4, 1, 4, 6, 1, 6, Direction.DOWN);
        caption(scene, util, LARGE, bottom, "storey");
        scene.overlay().showControls(util.vector().topOf(bottom.east()), Pointing.DOWN, 40).rightClick().withItem(CEReactor.LARGE_ABSORPTION_TOWER.asItem().getDefaultInstance());
        scene.idle(10);
        PonderText.build(scene, util, 4, 2, 4, 6, 4, 6, Direction.DOWN);
        caption(scene, util, LARGE, bottom.above(2), "same");
        PonderText.build(scene, util, 7, 1, 5, 9, 1, 5, Direction.WEST);
        PonderText.build(scene, util, 1, 1, 5, 3, 1, 5, Direction.EAST);
        PonderText.build(scene, util, 7, 4, 5, 9, 4, 5, Direction.WEST);
        PonderText.build(scene, util, 5, 5, 5, 5, 7, 5, Direction.DOWN);
        caption(scene, util, LARGE, util.grid().at(6, 1, 5), "ring");
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 50).withItem(AllItems.GOGGLES.asStack());
        caption(scene, util, LARGE, top, "observe");
        var wash = new net.neoforged.neoforge.fluids.FluidStack(CEChlorAlkali.CAUSTIC_SODA.get(), 1000);
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 50).withItem(wash.getFluidType().getBucket(wash));
        caption(scene, util, LARGE, top, "wash");
        caption(scene, util, LARGE, bottom, "recover");
        scene.markAsFinished();
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, String lang, BlockPos target, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), lang + key, 140);
    }
}
