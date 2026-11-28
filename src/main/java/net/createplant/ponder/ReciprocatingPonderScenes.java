package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Assembling and inspecting a horizontal opposed compressor. */
public final class ReciprocatingPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.reciprocating_compressor.";

    private ReciprocatingPonderScenes() { }

    public static void compressor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("reciprocating_compressor", PonderText.title(LANG + "title", "Reciprocating compressor"));
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.9f);
        scene.showBasePlate();
        var front = util.grid().at(5, 2, 4);
        var target = front.south();
        var engines = util.select().fromTo(front, front.south(2));
        var left = util.select().fromTo(3, 2, 4, 3, 2, 6);
        var right = util.select().fromTo(7, 2, 4, 7, 2, 6);
        var shaft = util.select().fromTo(5, 2, 7, 5, 2, 8);
        var suction = util.select().fromTo(3, 3, 4, 3, 3, 6).add(util.select().fromTo(7, 3, 4, 7, 3, 6));
        var discharge = util.select().fromTo(3, 1, 4, 3, 1, 6).add(util.select().fromTo(7, 1, 4, 7, 1, 6));
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(1, 1, 1, 9, 1, 9).substract(discharge), Direction.UP);
        scene.world().showSection(engines, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "engine", engines, 110);
        text(scene, util, target, 110, "engine");

        scene.world().showSection(left, Direction.EAST);
        for (int row = 0; row < 3; row++) showSides(scene, front.south(row), 1);
        scene.idle(18);
        scene.world().showSection(right, Direction.WEST);
        for (int row = 0; row < 3; row++) showSides(scene, front.south(row), 3);
        text(scene, util, target.east(2), 120, "cylinder");

        scene.world().showSection(shaft, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "shaft", util.select().position(front.south(3)), 120);
        text(scene, util, front.south(3), 120, "shaft");

        scene.world().showSection(suction.copy().add(discharge), Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.INPUT, "suction", suction, 60);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "discharge", discharge, 60);
        text(scene, util, front.west(2).above(), 130, "pipes");

        scene.overlay().showControls(util.vector().topOf(target), Pointing.DOWN, 45).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(14);
        scene.world().modifyBlocks(util.select().position(target), state -> state.setValue(ReciprocatingCompressorBlock.WINDOW, true), false);
        showSides(scene, target, 3);
        scene.world().setKineticSpeed(shaft, 32);
        text(scene, util, target, 140, "rotate");

        scene.overlay().showControls(util.vector().topOf(target), Pointing.DOWN, 40).withItem(AllItems.GOGGLES.asStack());
        text(scene, util, target, 130, "setting");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }

    private static void showSides(CreateSceneBuilder scene, BlockPos pos, int sides) {
        scene.world().modifyBlockEntity(pos, ReciprocatingCompressorBlockEntity.class, drive -> {
            var registry = drive.getLevel().registryAccess();
            var packet = drive.writeClient(new CompoundTag(), registry);
            packet.putInt("ConnectedSides", sides);
            drive.readClient(packet, registry);
        });
    }
}
