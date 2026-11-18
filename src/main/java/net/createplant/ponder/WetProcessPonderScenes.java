package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.neoforged.neoforge.fluids.FluidStack;

public final class WetProcessPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.wet_processing.";
    private WetProcessPonderScenes() { }
    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("wet_processing", PonderText.title(LANG + "title", "Wet leaching and clarification"));
        scene.configureBasePlate(0, 0, 27);scene.scaleSceneView(.40f);scene.showBasePlate();
        var leach = new BlockPos(4, 2, 5);var clarifier = new BlockPos(17, 5, 13);
        PonderText.build(scene, util, 4, 1, 5, 6, 3, 7, Direction.DOWN);
        caption(scene, util, leach, "build");
        scene.world().showSection(util.select().fromTo(1, 1, 11, 5, 3, 13), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(2, 2, 12), 64);scene.world().setKineticSpeed(util.select().position(4, 2, 12), -64);
        scene.world().showSection(util.select().position(7, 3, 6), Direction.WEST);
        scene.overlay().showControls(util.vector().centerOf(7, 3, 6), Pointing.DOWN, 60).withItem(AllItems.CRUSHED_COPPER.asStack());
        caption(scene, util, leach, "ore");
        scene.world().showSection(util.select().fromTo(5, 2, 8, 5, 2, 11), Direction.NORTH);
        scene.world().showSection(util.select().fromTo(7, 3, 2, 7, 3, 5), Direction.SOUTH);
        caption(scene, util, leach, "inlets");
        scene.world().setKineticSpeed(util.select().fromTo(4, 1, 5, 6, 3, 7), 64);
        scene.world().modifyBlockEntity(leach, net.createplant.block.entity.LiddedVesselBlockEntity.class, be -> {
            be.getTankInventory().fill(new FluidStack(CEWetProcessing.SLURRIES.get("copper_leach_slurry").get(), 6000), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        });
        caption(scene, util, leach, "stir");
        PonderText.build(scene, util, 11, 2, 7, 24, 5, 20, Direction.DOWN);
        scene.world().showSection(util.select().everywhere().substract(util.select().layer(0)), Direction.DOWN);
        scene.world().setKineticSpeed(util.select().position(7, 2, 3), 64);
        scene.world().setKineticSpeed(util.select().position(5, 2, 9), 64);
        scene.world().setKineticSpeed(util.select().position(17, 1, 7), 64);
        scene.world().setKineticSpeed(util.select().position(17, 5, 22), 64);
        caption(scene, util, clarifier.below(), "clarifier");
        scene.world().setKineticSpeed(util.select().fromTo(17, 5, 13, 17, 7, 13), 2);
        preview(scene, clarifier, "clarifying");
        scene.overlay().showControls(util.vector().centerOf(17, 2, 6), Pointing.DOWN, 60).withItem(CEWetProcessing.FLOCCULANT.toStack());
        caption(scene, util, clarifier, "slow");
        scene.world().setKineticSpeed(util.select().fromTo(17, 5, 13, 17, 7, 13), 12);preview(scene, clarifier, "dirty");
        caption(scene, util, clarifier, "fast");
        scene.world().setKineticSpeed(util.select().fromTo(17, 5, 13, 17, 7, 13), 20);preview(scene, clarifier, "turbulent");scene.idle(80);
        scene.world().setKineticSpeed(util.select().fromTo(17, 5, 13, 17, 7, 13), 0);preview(scene, clarifier, "stagnant");
        caption(scene, util, new BlockPos(17, 2, 7), "stopped");
        scene.world().setKineticSpeed(util.select().fromTo(17, 5, 13, 17, 7, 13), 2);preview(scene, clarifier, "clarifying");
        scene.overlay().showControls(util.vector().centerOf(17, 2, 6), Pointing.DOWN, 60).withItem(CEWetProcessing.TAILINGS.toStack());
        caption(scene, util, new BlockPos(17, 5, 20), "recycle");scene.markAsFinished();
    }
    private static void preview(CreateSceneBuilder scene, BlockPos pos, String status) {
        scene.world().modifyBlockEntity(pos, WetProcessBlockEntity.class, be -> {
            var slurry = new FluidStack(CEWetProcessing.SLURRIES.get("copper_leach_slurry").get(), 24000);
            var clear = new FluidStack(CEMetals.COPPER_SULFATE_SOLUTION.get(), 16000);
            if (status.equals("dirty")) clear.set(CEThermal.SUSPENDED_IMPURITY.get(), 615);
            be.preview(14, 4, slurry, status.equals("turbulent") ? slurry : clear, status);
        });
    }
    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) { PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 150); }
}
