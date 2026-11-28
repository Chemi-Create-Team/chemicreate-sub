package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The fuel-cycle and fusion-maintenance scenes of the plant systems chapter. */
public final class AtomicsSystemsPonderScenes {
    private AtomicsSystemsPonderScenes() { }
    public static void fuelCycle(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "fuel_cycle";var scene = PlantSystemsPonderScenes.begin(builder, id, 19, .35f);
        PonderText.build(scene, util, 3,1,2,5,1,5, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "feed", new BlockPos(4, 1, 2));
        scene.world().setKineticSpeed(util.select().position(4, 1, 2), 64);
        PonderText.build(scene, util, 3,2,3,5,12,5, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "column", new BlockPos(4, 7, 4));
        net.createmod.ponder.api.scene.Selection products = null;
        for (int storey : net.createplant.block.FuelCycleStructure.OUTLETS) {
            var level = util.select().fromTo(3, storey + 1, 3, 5, storey + 1, 5);
            products = products == null ? level : products.add(level);
        }
        scene.overlay().showOutline(PonderPalette.OUTPUT, "products", products, 130);
        PlantSystemsPonderScenes.text(scene, util, id, "outlets", new BlockPos(5, 5, 4));
        scene.world().setKineticSpeed(util.select().position(3, 3, 12).add(util.select().position(6, 3, 12)), 64);
        PonderText.build(scene, util, 3,1,12,6,3,12, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "mox", new BlockPos(3, 2, 12));
        PonderText.build(scene, util, 11,1,3,14,4,9, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "melter", new BlockPos(12, 1, 3));
        PonderText.build(scene, util, 15,1,3,15,3,3, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "glass", new BlockPos(15, 2, 3));
        PlantSystemsPonderScenes.text(scene, util, id, "stop", new BlockPos(12, 1, 3));scene.markAsFinished();
    }
    public static void maintenance(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "fusion_maintenance";var scene = PlantSystemsPonderScenes.begin(builder, id, 17, .15f);scene.setSceneOffsetY(-6);
        for (var cell : net.createplant.fusion.FusionStructure.cells(new BlockPos(8, 5, 3), Direction.SOUTH)) scene.world().setBlock(cell.pos(), cell.expected(), false);
        PonderText.build(scene, util, 3,1,3,13,8,13, Direction.DOWN);
        PlantSystemsPonderScenes.text(scene, util, id, "reactor", new BlockPos(8, 5, 3));
        PonderText.build(scene, util, 2,1,2,14,13,14, Direction.DOWN);
        PonderText.build(scene, util, 0,14,2,14,15,14, Direction.DOWN);
        fusionHoist(scene, util, 2, 4, 0, 0);
        PlantSystemsPonderScenes.text(scene, util, id, "build", new BlockPos(8, 14, 8));
        PlantSystemsPonderScenes.text(scene, util, id, "safe", new BlockPos(8, 5, 3));
        PlantSystemsPonderScenes.text(scene, util, id, "supply", new BlockPos(1, 14, 2));
        scene.world().setKineticSpeed(util.select().fromTo(1, 14, 2, 14, 14, 14), 64);
        fusionHoist(scene, util, 6, 2, 5, 0);
        PlantSystemsPonderScenes.text(scene, util, id, "exchange", new BlockPos(8, 8, 4));
        fusionHoist(scene, util, 6, 2, 5, 1);
        PlantSystemsPonderScenes.text(scene, util, id, "damage", new BlockPos(1, 14, 2));
        scene.overlay().showControls(util.vector().centerOf(new BlockPos(1, 14, 2)), Pointing.RIGHT, 70).withItem(new net.minecraft.world.item.ItemStack(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get()));
        PlantSystemsPonderScenes.text(scene, util, id, "irradiation", new BlockPos(1, 14, 2));scene.markAsFinished();
    }
    private static void fusionHoist(CreateSceneBuilder scene, SceneBuildingUtil util, double u, double v, double depth, int component) {
        scene.world().modifyBlockEntityNBT(util.select().position(1, 14, 2), net.createplant.block.entity.CraneDriveBlockEntity.class, tag -> {
            tag.putLong("Start", new BlockPos(2, 14, 2).asLong());
            tag.putInt("Along", Direction.EAST.get3DDataValue());tag.putInt("Across", Direction.SOUTH.get3DDataValue());
            tag.putInt("Length", 13);tag.putInt("Span", 12);tag.putInt("Legs", 4);
            tag.putDouble("U", u);tag.putDouble("V", v);tag.putDouble("Depth", depth);
            tag.putInt("FusionComponent", component);tag.putBoolean("Working", depth > 0);
        });
        scene.idle(20);
    }
}
