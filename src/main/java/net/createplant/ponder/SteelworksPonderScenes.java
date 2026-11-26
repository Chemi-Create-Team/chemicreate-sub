package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.registry.CEConverter;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class SteelworksPonderScenes {
    private static final String TEXT = "ponder.create_refinery_n_reactors.torpedo_car.";
    private static final String STAINLESS = "ponder.create_refinery_n_reactors.stainless_casting.";

    private SteelworksPonderScenes() { }

    public static void stainless(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("stainless_casting", PonderText.title(STAINLESS + "title", "Mixing and casting stainless steel"));
        scene.configureBasePlate(0, 0, 12);
        scene.scaleSceneView(.65f);
        scene.showBasePlate();

        var mixer = util.select().fromTo(3, 2, 5, 3, 6, 6);
        scene.world().showSection(mixer, Direction.DOWN);
        scene.world().setKineticSpeed(mixer, 128);
        PonderText.caption(scene, util.vector().centerOf(util.grid().at(3, 3, 5)), STAINLESS + "mix", 120);

        var pipes = util.select().fromTo(4, 2, 5, 8, 3, 5);
        var caster = util.select().fromTo(9, 1, 5, 10, 5, 5);
        scene.world().showSection(caster, Direction.DOWN);
        scene.world().showSection(pipes, Direction.WEST);
        scene.world().setKineticSpeed(pipes.copy().add(caster), 128);
        scene.overlay().showOutline(PonderPalette.INPUT, "stainless_pipe", util.select().fromTo(4, 3, 5, 8, 3, 5), 120);
        PonderText.caption(scene, util.vector().centerOf(util.grid().at(6, 3, 5)), STAINLESS + "pump", 120);
        var output = util.grid().at(9, 1, 5);
        scene.overlay().showControls(util.vector().topOf(output), Pointing.DOWN, 100)
            .withItem(CEConverter.CAST_STAINLESS_STEEL_SLAB.toStack());
        PonderText.caption(scene, util.vector().centerOf(output), STAINLESS + "cast", 120);
        scene.markAsFinished();
    }

    public static void torpedo(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("torpedo_car", PonderText.title(TEXT + "title", "From pig iron to rolled steel"));
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(.55f);
        scene.showBasePlate();

        var press = util.grid().at(8, 6, 3);
        scene.world().showSection(util.select().fromTo(8, 3, 3, 8, 6, 3), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(press.below(2)), Pointing.DOWN, 100)
            .withItem(CEConverter.SILICA_BRICKS.toStack());
        caption(scene, util, press.below(2), "bricks");

        scene.world().showSection(util.select().fromTo(1, 1, 6, 11, 1, 6), Direction.DOWN);
        scene.world().showSection(util.select().position(2, 1, 8), Direction.DOWN);
        var tank = util.select().fromTo(3, 3, 5, 7, 5, 7);
        var bogeys = util.select().position(3, 2, 6).add(util.select().position(7, 2, 6));
        var movingPort = util.select().position(5, 4, 4);
        scene.world().showSection(bogeys, Direction.DOWN);
        PonderText.build(scene, util, 3, 3, 5, 7, 5, 7, Direction.DOWN);
        scene.world().showSection(movingPort, Direction.SOUTH);
        var carriage = bogeys.copy().add(tank).add(movingPort);
        var cell = util.grid().at(5, 5, 6);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "torpedo", tank, 120);
        scene.overlay().showControls(util.vector().topOf(cell), Pointing.DOWN, 100).rightClick()
            .withItem(AllItems.SUPER_GLUE.asStack());
        caption(scene, util, cell, "assemble");

        var port = util.grid().at(5, 4, 1);
        scene.world().showSection(util.select().fromTo(5, 4, 0, 5, 4, 1), Direction.SOUTH);
        scene.overlay().showOutline(PonderPalette.INPUT, "torpedo_transfer", util.select().fromTo(5, 4, 1, 5, 4, 4), 120);
        caption(scene, util, port, "transfer");
        var train = scene.world().makeSectionIndependent(carriage);
        scene.world().moveSection(train, new Vec3(3, 0, 0), 50);
        scene.idle(55);

        caption(scene, util, null, "refine");
        var cast = util.grid().at(11, 4, 3);
        scene.world().showSection(util.select().fromTo(11, 4, 3, 11, 6, 4), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(cast), Pointing.DOWN, 100).withItem(CEConverter.CAST_STEEL_SLAB.toStack());
        caption(scene, util, cast, "cast");
        scene.markAsFinished();
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, pos == null ? null : util.vector().centerOf(pos), TEXT + key, 120);
    }
}
