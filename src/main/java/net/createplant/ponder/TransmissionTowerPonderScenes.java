package net.createplant.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.registry.CETransmission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class TransmissionTowerPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.transmission_tower.";

    private TransmissionTowerPonderScenes() { }

    public static void connection(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("transmission_tower", PonderText.title(LANG + "title", "Stringing a transmission line"));
        scene.configureBasePlate(0, 0, 19);
        scene.scaleSceneView(.195f);
        scene.setSceneOffsetY(-9);
        scene.showBasePlate();
        var first = new BlockPos(9, 1, 4);
        var second = new BlockPos(9, 1, 14);
        scene.world().showSection(util.select().position(first), Direction.DOWN);
        PonderText.caption(scene, util.vector().centerOf(first.above(6)), LANG + "placement", 100);
        scene.world().showSection(util.select().position(second), Direction.DOWN);
        scene.overlay().showControls(Vec3.atLowerCornerOf(first).add(.5, .90, .265), Pointing.DOWN, 55)
            .rightClick().withItem(CETransmission.SPOOL.toStack());
        PonderText.caption(scene, util.vector().topOf(first), LANG + "terminal", 95);
        scene.overlay().showControls(Vec3.atLowerCornerOf(second).add(.5, .90, .265), Pointing.DOWN, 55)
            .rightClick().withItem(CETransmission.SPOOL.toStack());

        var wires = new WireConnectionInstructions(builder);
        wires.createConnection(new InWorldNode(1, first), new InWorldNode(1, second), CETransmission.wire());
        PonderText.caption(scene, new Vec3(5.08, 19.82, 9.5), LANG + "span", 130);
        for (int id : new int[]{0, 2, 3, 4, 5}) {
            wires.createConnection(new InWorldNode(id, first), new InWorldNode(id, second), CETransmission.wire());
            scene.idle(5);
        }
        PonderText.caption(scene, new Vec3(13.92, 19.82, 10.5), LANG + "circuit", 130);
        scene.markAsFinished();
    }
}
