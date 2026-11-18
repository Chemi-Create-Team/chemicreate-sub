package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.registry.CEChlorAlkali;
import net.createplant.registry.CENuclear;
import net.createplant.registry.CERadiation;
import net.createplant.registry.CESafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class RadiationPonderScenes {
    private RadiationPonderScenes() {}

    private static CreateSceneBuilder setup(SceneBuilder builder) {
        var scene = new CreateSceneBuilder(builder);
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        return scene;
    }

    public static void measurement(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = setup(builder);
        String key = "ponder.create_refinery_n_reactors.radiation_measurement.";
        scene.title(
                "radiation_measurement",
                PonderText.title(key + "title", "Measure radiation before handling fuel"));
        var source = util.select().position(6, 1, 4);
        var wall = util.select().fromTo(4, 1, 3, 4, 3, 5);
        scene.world().showSection(source, Direction.DOWN);
        scene.world()
                .modifyBlockEntity(
                        new BlockPos(6, 1, 4),
                        ChestBlockEntity.class,
                        chest -> chest.setItem(0, CENuclear.SPENT_FUEL_ROD.toStack()));
        scene.overlay().showOutline(PonderPalette.RED, "source", source, 100);
        scene.overlay()
                .showControls(util.vector().of(2, 1, 4), Pointing.DOWN, 90)
                .withItem(CERadiation.DOSIMETER.toStack());
        PonderText.caption(scene, util.vector().of(2, 1, 4), key + "badge", 100);
        scene.overlay()
                .showControls(util.vector().of(3, 1, 4), Pointing.DOWN, 100)
                .rightClick()
                .withItem(CERadiation.SURVEY_METER.toStack());
        PonderText.caption(scene, util.vector().of(6, 1, 4), key + "survey", 120);
        scene.world().showSection(wall, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "shield", wall, 120);
        PonderText.caption(scene, util.vector().of(4, 2, 4), key + "shield", 120);
        scene.markAsFinished();
    }

    public static void decontamination(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = setup(builder);
        String key = "ponder.create_refinery_n_reactors.radiation_decontamination.";
        scene.title(
                "radiation_decontamination",
                PonderText.title(key + "title", "Contain and wash radioactive contamination"));
        scene.world().showSection(util.select().fromTo(5, 1, 3, 6, 1, 3), Direction.DOWN);
        var spill = util.select().fromTo(4, 0, 3, 5, 0, 4);
        scene.overlay().showOutline(PonderPalette.RED, "spill", spill, 110);
        PonderText.caption(scene, util.vector().of(5, 1, 3), key + "contain", 100);
        scene.overlay()
                .showControls(util.vector().of(4, 1, 5), Pointing.DOWN, 100)
                .withItem(CESafety.GAS_MASK.toStack());
        PonderText.caption(scene, util.vector().of(4, 1, 5), key + "protect", 120);
        scene.world().showSection(util.select().fromTo(1, 1, 5, 3, 1, 7), Direction.DOWN);
        scene.overlay()
                .showControls(util.vector().of(2, 1, 6), Pointing.DOWN, 100)
                .rightClick()
                .withItem(CEChlorAlkali.SOAP.toStack());
        scene.overlay()
                .showOutline(PonderPalette.BLUE, "wash", util.select().position(2, 1, 6), 120);
        PonderText.caption(scene, util.vector().of(2, 1, 6), key + "wash", 120);
        scene.markAsFinished();
    }
}
