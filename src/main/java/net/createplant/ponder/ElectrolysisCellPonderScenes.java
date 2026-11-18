package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.createplant.registry.CEElectrolysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import static net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;

public final class ElectrolysisCellPonderScenes {
    private static final String LANG = "ponder." + CreatePlant.MOD_ID + ".electrolysis_cell.";
    private ElectrolysisCellPonderScenes() { }

    public static void cells(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("electrolysis_cell", PonderText.title(LANG + "title", "Filter-press electrolysis cells"));
        scene.configureBasePlate(0, 0, 13);scene.scaleSceneView(.64f);scene.showBasePlate();scene.idle(10);
        scene.world().showSection(util.select().fromTo(5, 1, 2, 5, 2, 2), Direction.DOWN);text(scene, util, new BlockPos(5, 2, 2), "shape", 130);
        scene.world().showSection(util.select().fromTo(5, 1, 3, 5, 2, 9), Direction.NORTH);text(scene, util, new BlockPos(5, 2, 7), "extend", 130);
        PonderText.build(scene, util, 5, 2, 1, 5, 3, 1, Direction.SOUTH);
        text(scene, util, new BlockPos(5, 2, 1), "panel", 120);
        scene.world().showSection(util.select().fromTo(5, 3, 2, 5, 3, 9), Direction.DOWN);
        text(scene, util, new BlockPos(5, 3, 6), "head", 130);
        var rack = new BlockPos(5, 3, 4);
        scene.overlay().showControls(util.vector().topOf(rack), Pointing.DOWN, 110).rightClick().withItem(CEElectrolysis.ELECTRODE_PLATE.toStack());
        for (int z = 2; z <= 9; z++)
            scene.world().modifyBlockEntity(new BlockPos(5, 3, z), ElectrodeHeadBlockEntity.class, be -> be.electrodes.insertItem(0, CEElectrolysis.ELECTRODE_PLATE.toStack(16), false));
        text(scene, util, rack, "plates", 160);
        var pipes = util.select().everywhere().substract(util.select().fromTo(5, 1, 1, 5, 4, 9)).substract(util.select().layer(0));
        scene.world().showSection(pipes, Direction.DOWN);
        scene.world().setKineticSpeed(pipes, 64);
        var panel = new BlockPos(5, 2, 1);
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> {
            be.validate();be.cells().getFirst().port(Direction.DOWN).fill(new FluidStack(Fluids.WATER, 14000), EXECUTE);
        });
        text(scene, util, new BlockPos(4, 1, 3), "feed", 150);
        text(scene, util, new BlockPos(5, 3, 9), "rotation", 170);
        var spool = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "copper_wire_spool"));
        var terminal = new BlockPos(5, 3, 1);
        scene.overlay().showControls(util.vector().topOf(terminal), Pointing.DOWN, 130).rightClick().withItem(new ItemStack(spool));
        var wires = new com.george_vi.electroenergetics.ponder.WireConnectionInstructions(scene);
        for (int node=0;node<2;node++)
            wires.createConnection(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(node,new BlockPos(3,1,0)),
                new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(node,terminal));
        text(scene, util, terminal, "wire", 170);
        scene.overlay().showControls(util.vector().of(5.75,3.2125,.97), Pointing.LEFT, 100).rightClick();
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> { be.ponderVisual(false);be.setRunning(true); });
        text(scene, util, new BlockPos(5, 3, 1), "run", 160);
        text(scene, util, new BlockPos(6, 2, 3), "gas", 150);
        scene.overlay().showControls(util.vector().of(5.5,2.4375,.97), Pointing.DOWN, 100).rightClick();
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> { be.selectLoad(37);be.ponderVisual(false); });
        text(scene, util, panel, "controls", 170);
        scene.overlay().showControls(util.vector().of(5.25,3.2125,.97), Pointing.LEFT, 100).rightClick();
        scene.world().modifyBlockEntity(panel, ElectrolysisControllerBlockEntity.class, be -> { be.setRunning(false);be.ponderVisual(true); });
        text(scene, util, rack, "blocked", 160);scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, String key, int ticks) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }
}
