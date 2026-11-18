package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.entity.*;
import net.createplant.registry.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class MetalRecoveryPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.";
    private MetalRecoveryPonderScenes() { }
    public static void preparation(SceneBuilder builder, SceneBuildingUtil util) {
        WetProcessPonderScenes.processing(builder, util);
    }
    public static void electrowinning(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = begin(builder, "electrowinning", 13);scene.world().showSection(util.select().everywhere().substract(util.select().layer(0)), Direction.DOWN);
        var panel = new BlockPos(5, 2, 4);
        scene.world().modifyBlockEntity(new BlockPos(5, 3, 5), ElectrodeHeadBlockEntity.class, be -> be.electrodes.insertItem(0, CEElectrolysis.ELECTRODE_PLATE.toStack(), false));
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> { be.validate();be.cells().getFirst().port(Direction.DOWN).fill(new FluidStack(CEMetals.COPPER_SULFATE_SOLUTION.get(), 1000), EXECUTE);be.ponderVisual(false);be.setRunning(true); });
        text(scene, util, new BlockPos(4, 1, 6), "electrowinning", "feed", 160);
        var wires = new com.george_vi.electroenergetics.ponder.WireConnectionInstructions(scene);
        for (int node = 0; node < 2; node++) wires.createConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(node, new BlockPos(3, 1, 3)), new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(node, new BlockPos(5, 3, 5)));
        text(scene, util, panel, "electrowinning", "power", 180);
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> { be.heads().getFirst().products.setStackInSlot(0, net.minecraft.world.item.Items.COPPER_INGOT.getDefaultInstance().copyWithCount(3));be.cells().getFirst().tanks.get(3).fill(new FluidStack(CEMetals.DILUTE_SULFURIC_ACID.get(), 250), EXECUTE);be.setRunning(false);be.ponderVisual(true); });
        scene.overlay().showControls(util.vector().topOf(new BlockPos(5, 3, 5)), Pointing.DOWN, 90).withItem(net.minecraft.world.item.Items.COPPER_INGOT.getDefaultInstance());
        text(scene, util, new BlockPos(5, 3, 5), "electrowinning", "metal", 170);
        text(scene, util, new BlockPos(7, 2, 7), "electrowinning", "acid", 180);scene.markAsFinished();
    }
    private static CreateSceneBuilder begin(SceneBuilder builder, String id, int width) {
        var scene = new CreateSceneBuilder(builder);scene.title(id, PonderText.title(LANG + id + ".title", id));scene.configureBasePlate(0, 0, width);scene.scaleSceneView(.7f);scene.showBasePlate();return scene;
    }
    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String sceneId, String key, int ticks) { PonderText.caption(scene, util.vector().centerOf(pos), LANG + sceneId + "." + key, ticks); }
}
