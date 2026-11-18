package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.GasCentrifugeBlock;
import net.createplant.block.entity.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.PipeBlock;

public final class HighStressPonderScenes {
    private HighStressPonderScenes() { }
    public static void centrifuge(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);String lang = "ponder.create_refinery_n_reactors.gas_centrifuge.";
        scene.title("gas_centrifuge", PonderText.title(lang + "title", "Cascading gas centrifuges"));scene.configureBasePlate(0, 0, 8);scene.showBasePlate();
        var first = util.grid().at(2, 2, 3);
        scene.world().modifyBlocks(util.select().fromTo(2, 3, 3, 5, 3, 4), state -> state.setValue(PipeBlock.NORTH, false).setValue(PipeBlock.SOUTH, false).setValue(PipeBlock.EAST, false).setValue(PipeBlock.WEST, false), false);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 3, 3), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(first.below()).add(util.select().position(first)), 32);
        scene.world().modifyBlock(first.above(), state -> state.setValue(GasCentrifugeBlock.ACTIVE, true), false);
        PonderText.caption(scene, util.vector().centerOf(first.below()), lang + "drive", 120);
        PonderText.caption(scene, util.vector().centerOf(first), lang + "feed", 120);
        PonderText.caption(scene, util.vector().topOf(first.above()), lang + "product", 120);
        PonderText.caption(scene, util.vector().centerOf(first.above()), lang + "tails", 120);
        for (int x = 3; x <= 5; x++) scene.world().showSection(util.select().fromTo(x, 1, 3, x, 3, 3), Direction.DOWN);
        connect(scene, util, false);PonderText.caption(scene, util.vector().centerOf(first.east()), lang + "stages", 130);
        for (int x = 2; x <= 5; x++) scene.world().showSection(util.select().fromTo(x, 1, 4, x, 3, 4), Direction.DOWN);
        connect(scene, util, true);PonderText.caption(scene, util.vector().centerOf(first.east(2)), lang + "eight", 140);
        scene.world().setKineticSpeed(util.select().fromTo(2, 1, 3, 5, 2, 4), 0);
        scene.world().modifyBlocks(util.select().fromTo(2, 3, 3, 5, 3, 4), state -> state.setValue(GasCentrifugeBlock.ACTIVE, false), false);
        scene.overlay().showControls(util.vector().topOf(first), Pointing.DOWN, 60).withItem(AllItems.GOGGLES.asStack());
        PonderText.caption(scene, util.vector().centerOf(first), lang + "stop", 120);scene.markAsFinished();
    }
    private static void connect(CreateSceneBuilder scene, SceneBuildingUtil util, boolean grid) {
        for (int x = 2; x <= 5; x++) for (int z = 3; z <= (grid ? 4 : 3); z++) {
            final int column = x, row = z;
            scene.world().modifyBlock(util.grid().at(x, 3, z), state -> state.setValue(PipeBlock.WEST, column > 2).setValue(PipeBlock.EAST, column < 5)
                .setValue(PipeBlock.NORTH, grid && row == 4).setValue(PipeBlock.SOUTH, grid && row == 3).setValue(GasCentrifugeBlock.ACTIVE, true), false);
            scene.world().setKineticSpeed(util.select().fromTo(x, 1, z, x, 2, z), 32);
        }
    }
    public static void press(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);String lang = "ponder.create_refinery_n_reactors.cubic_press.";
        scene.title("cubic_press", PonderText.title(lang + "title", "Pressing synthetic diamonds"));scene.configureBasePlate(0, 0, 7);scene.showBasePlate();
        var machine = util.grid().at(3, 2, 3);
        scene.world().showSection(util.select().fromTo(3, 1, 3, 3, 2, 3), Direction.DOWN);
        scene.world().modifyBlock(machine.below(), state -> state.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL, com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING), false);
        scene.overlay().showControls(util.vector().topOf(machine.below()), Pointing.DOWN, 60).rightClick().withItem(AllItems.BLAZE_CAKE.asStack());
        PonderText.caption(scene, util.vector().centerOf(machine.below()), lang + "heat", 110);
        scene.world().showSection(util.select().fromTo(4, 2, 3, 5, 2, 3), Direction.WEST);
        scene.world().setKineticSpeed(util.select().fromTo(3, 2, 3, 5, 2, 3), 64);PonderText.caption(scene, util.vector().centerOf(machine.east()), lang + "drive", 110);
        scene.world().showSection(util.select().position(machine.north()), Direction.SOUTH);PonderText.caption(scene, util.vector().centerOf(machine.north()), lang + "items", 120);
        scene.world().modifyBlockEntity(machine, CubicPressBlockEntity.class, be -> be.preview(true));
        PonderText.caption(scene, util.vector().centerOf(machine), lang + "run", 140);
        scene.world().modifyBlockEntity(machine, CubicPressBlockEntity.class, be -> be.preview(false));
        scene.world().setKineticSpeed(util.select().fromTo(3, 2, 3, 5, 2, 3), 0);PonderText.caption(scene, util.vector().centerOf(machine), lang + "stop", 110);scene.markAsFinished();
    }
}
