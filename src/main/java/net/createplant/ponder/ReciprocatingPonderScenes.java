package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The cylinder first, then the engine on its side, the crank shaft, the gas pipes, the rotation, and the output grade. */
public final class ReciprocatingPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.reciprocating_compressor.";

    private ReciprocatingPonderScenes() { }

    public static void compressor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("reciprocating_compressor", PonderText.title(LANG + "title", "Reciprocating compressor"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        var cylinder = util.select().position(2, 2, 3);
        var engine = util.select().position(3, 2, 3);
        var shaft = util.select().fromTo(5, 2, 1, 5, 2, 5);
        var pipes = util.select().fromTo(2, 3, 3, 2, 4, 3).add(util.select().position(2, 1, 3));
        scene.idle(10);

        scene.world().showSection(cylinder, Direction.DOWN);
        text(scene, util, util.grid().at(2, 2, 3), 110, "cylinder");

        scene.world().showSection(engine, Direction.WEST);
        scene.overlay().showOutline(PonderPalette.GREEN, "engine", engine, 110);
        text(scene, util, util.grid().at(3, 2, 3), 110, "engine");

        scene.world().showSection(shaft, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "shaft", util.select().position(5, 2, 3), 120);
        text(scene, util, util.grid().at(5, 2, 3), 120, "shaft");

        scene.world().showSection(pipes, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.INPUT, "suction", util.select().fromTo(2, 3, 3, 2, 4, 3), 60);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "discharge", util.select().position(2, 1, 3), 60);
        text(scene, util, util.grid().at(2, 3, 3), 130, "pipes");

        scene.world().setKineticSpeed(shaft.add(util.select().position(3, 2, 3)), 32);
        text(scene, util, util.grid().at(5, 2, 3), 130, "rotate");

        BlockPos target = util.grid().at(3, 2, 3);
        text(scene, util, target, 130, "setting");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }
}
