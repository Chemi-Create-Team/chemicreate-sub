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
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        var left = util.grid().at(2, 2, 3);
        var right = util.grid().at(6, 2, 3);
        var target = util.grid().at(4, 2, 3);
        var engine = util.select().position(target);
        var shaft = util.select().fromTo(4, 2, 4, 4, 2, 5);
        var suction = util.select().position(left.above()).add(util.select().position(right.above()));
        var discharge = util.select().position(left.below()).add(util.select().position(right.below()));
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(1, 1, 2, 7, 1, 5).substract(discharge), Direction.UP);
        scene.world().showSection(engine, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.GREEN, "engine", engine, 110);
        text(scene, util, target, 110, "engine");

        scene.world().showSection(util.select().position(left), Direction.EAST);
        showSides(scene, target, 1);
        scene.idle(18);
        scene.world().showSection(util.select().position(right), Direction.WEST);
        showSides(scene, target, 3);
        text(scene, util, right, 120, "cylinder");

        scene.world().showSection(shaft, Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.BLUE, "shaft", util.select().position(target.south()), 120);
        text(scene, util, target.south(), 120, "shaft");

        scene.world().showSection(suction.add(discharge), Direction.DOWN);
        scene.overlay().showOutline(PonderPalette.INPUT, "suction", suction, 60);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "discharge", discharge, 60);
        text(scene, util, left.above(), 130, "pipes");

        scene.overlay().showControls(util.vector().topOf(target), Pointing.DOWN, 45).rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(14);
        scene.world().modifyBlocks(engine, state -> state.setValue(ReciprocatingCompressorBlock.WINDOW, true), false);
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
