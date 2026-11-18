package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.entity.PipeGaugeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PipeGaugePonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.pipe_gauges.";
    private static final BlockPos PUMP = new BlockPos(1, 1, 2), THERMOMETER = new BlockPos(2, 1, 2), FLOW = new BlockPos(3, 1, 2), PRESSURE = new BlockPos(4, 1, 2), LAMP = new BlockPos(4, 2, 2), GAS = new BlockPos(3, 1, 5);

    private PipeGaugePonderScenes() { }

    public static void gauges(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("pipe_gauges", PonderText.title(LANG + "title", "Reading pipes with gauges"));
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.9f);
        scene.showBasePlate();
        PonderText.build(scene, util, 0, 1, 2, 5, 1, 2, Direction.DOWN);
        scene.idle(15);
        text(scene, util, THERMOMETER, "place");
        scene.world().setKineticSpeed(util.select().position(PUMP), 64);
        show(scene, util, THERMOMETER, 353.15);
        show(scene, util, FLOW, 64);
        show(scene, util, PRESSURE, 3e5);
        text(scene, util, PRESSURE, "reading");
        text(scene, util, THERMOMETER, "scale");
        text(scene, util, FLOW, "flow");
        scene.world().setKineticSpeed(util.select().position(PUMP), 0);
        show(scene, util, THERMOMETER, Double.NaN);
        show(scene, util, FLOW, 0);
        show(scene, util, PRESSURE, 1e5);
        text(scene, util, PRESSURE, "stale");
        PonderText.build(scene, util, 0, 1, 5, 5, 1, 5, Direction.DOWN);
        scene.idle(15);
        show(scene, util, GAS, 8e5);
        text(scene, util, GAS, "gas");
        scene.world().showSection(util.select().position(LAMP), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().blockSurface(PRESSURE, Direction.SOUTH), Pointing.RIGHT, 100).rightClick().withItem(AllItems.WRENCH.asStack());
        text(scene, util, PRESSURE, "alarm_set");
        scene.world().setKineticSpeed(util.select().position(PUMP), 64);
        show(scene, util, FLOW, 64);
        show(scene, util, PRESSURE, 12e5);
        scene.world().modifyBlock(LAMP, state -> state.setValue(BlockStateProperties.LIT, true), false);
        text(scene, util, LAMP, "alarm_signal");
        scene.overlay().showControls(util.vector().topOf(GAS), Pointing.DOWN, 100).withItem(AllItems.GOGGLES.asStack());
        text(scene, util, GAS, "display");
        scene.markAsFinished();
    }

    /** Ponder runs no flow; the gauge is handed the reading a running line would report. */
    private static void show(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, double value) {
        scene.world().modifyBlockEntity(pos, PipeGaugeBlockEntity.class, gauge -> gauge.preview(value));
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 120);
    }
}
