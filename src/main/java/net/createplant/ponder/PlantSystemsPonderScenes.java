package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class PlantSystemsPonderScenes {
    private PlantSystemsPonderScenes() { }
    private static CreateSceneBuilder begin(SceneBuilder builder, String id, int width, float scale) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(id, PonderText.title("ponder.create_refinery_n_reactors." + id + ".title", id));
        scene.configureBasePlate(0, 0, width);scene.scaleSceneView(scale);scene.showBasePlate();scene.idle(10);
        return scene;
    }
    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, String id, String key, BlockPos at) {
        PonderText.caption(scene, util.vector().centerOf(at), "ponder.create_refinery_n_reactors." + id + "." + key, 130);
    }
    public static void tower(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "natural_draft_tower";var scene = begin(builder, id, 27, .095f);scene.setSceneOffsetY(-14);
        BlockPos controller = new BlockPos(13, 2, 1);
        var tower = util.select().fromTo(1, 1, 1, 25, 36, 25).substract(util.select().position(controller));
        scene.world().setBlocks(tower, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
        scene.world().showSection(util.select().position(controller), Direction.DOWN);scene.idle(10);
        text(scene, util, id, "preview", controller);
        scene.world().restoreBlocks(tower);
        PonderText.build(scene, util, 1, 1, 1, 25, 7, 25, Direction.DOWN);
        text(scene, util, id, "fill", new BlockPos(13, 4, 5));
        PonderText.build(scene, util, 1, 8, 1, 25, 36, 25, Direction.DOWN);
        text(scene, util, id, "size", new BlockPos(13, 27, 6));
        scene.overlay().showOutline(PonderPalette.INPUT, "hot", util.select().fromTo(4, 2, 1, 22, 2, 6), 130);
        text(scene, util, id, "water", new BlockPos(13, 2, 1));
        text(scene, util, id, "climate", new BlockPos(13, 36, 13));
        text(scene, util, id, "stop", new BlockPos(13, 2, 1));scene.markAsFinished();
    }
    public static void condenser(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "condenser_loop";var scene = begin(builder, id, 25, .31f);
        scene.world().setBlock(new BlockPos(24,10,24), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
        for (var unit : TurbineUnitPonderScenes.layouts())
            if (unit.stage() == net.createplant.block.TurbineUnit.Stage.LOW) TurbineUnitPonderScenes.section(scene, unit);
        PonderText.build(scene, util, 7,4,16,9,4,21, Direction.DOWN);
        PonderText.build(scene, util, 8,5,16,8,5,20, Direction.DOWN);
        text(scene, util, id, "bundle", new BlockPos(8, 5, 18));
        PonderText.build(scene, util, 7,5,16,9,6,21, Direction.DOWN);
        PonderText.build(scene, util, 7,4,15,9,6,15, Direction.SOUTH);
        text(scene, util, id, "build", new BlockPos(8, 5, 15));
        PonderText.build(scene, util, 2,1,6,12,9,14, Direction.DOWN);
        PonderText.build(scene, util, 4,5,15,12,5,16, Direction.DOWN);
        scene.rotateCameraY(180);
        scene.idle(40);
        var pumps = util.select().position(5,5,15).add(util.select().position(11,5,15));
        scene.world().setKineticSpeed(pumps, 128);
        text(scene, util, id, "loop", new BlockPos(5, 5, 15));
        scene.overlay().showOutline(PonderPalette.BLUE,"condenser_cold",util.select().position(7,5,15),100);
        scene.overlay().showOutline(PonderPalette.RED,"condenser_hot",util.select().position(9,5,15),100);
        text(scene, util, id, "heat", new BlockPos(8, 5, 15));
        scene.world().setKineticSpeed(pumps, 0);
        text(scene, util, id, "stop", new BlockPos(8, 5, 15));scene.markAsFinished();
    }
    public static void pid(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "process_pid";var scene = begin(builder, id, 11, .65f);
        PonderText.build(scene, util, 2,1,1,2,3,3, Direction.DOWN);
        text(scene, util, id, "sensor", new BlockPos(2, 2, 2));
        PonderText.build(scene, util, 6,1,3,6,2,3, Direction.DOWN);
        scene.overlay().showControls(util.vector().centerOf(new BlockPos(6, 2, 3)), Pointing.DOWN, 70).withItem(AllBlocks.DISPLAY_LINK.asStack());
        text(scene, util, id, "rack", new BlockPos(6, 2, 3));
        scene.overlay().showControls(util.vector().blockSurface(new BlockPos(6, 2, 3), Direction.NORTH), Pointing.RIGHT, 70).rightClick();
        text(scene, util, id, "knobs", new BlockPos(6, 2, 3));
        scene.world().setKineticSpeed(util.select().position(8, 1, 1), 128);
        PonderText.build(scene, util, 8,1,1,8,2,3, Direction.DOWN);
        text(scene, util, id, "valve", new BlockPos(8, 2, 3));
        text(scene, util, id, "loops", new BlockPos(6, 1, 3));scene.markAsFinished();
    }
    public static void fuelCycle(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "fuel_cycle";var scene = begin(builder, id, 19, .35f);
        PonderText.build(scene, util, 3,1,2,5,1,5, Direction.DOWN);
        text(scene, util, id, "feed", new BlockPos(4, 1, 2));
        scene.world().setKineticSpeed(util.select().position(4, 1, 2), 64);
        PonderText.build(scene, util, 3,2,3,5,12,5, Direction.DOWN);
        text(scene, util, id, "column", new BlockPos(4, 7, 4));
        net.createmod.ponder.api.scene.Selection products = null;
        for (int storey : net.createplant.block.FuelCycleStructure.OUTLETS) {
            var level = util.select().fromTo(3, storey + 1, 3, 5, storey + 1, 5);
            products = products == null ? level : products.add(level);
        }
        scene.overlay().showOutline(PonderPalette.OUTPUT, "products", products, 130);
        text(scene, util, id, "outlets", new BlockPos(5, 5, 4));
        scene.world().setKineticSpeed(util.select().position(3, 3, 12).add(util.select().position(6, 3, 12)), 64);
        PonderText.build(scene, util, 3,1,12,6,3,12, Direction.DOWN);
        text(scene, util, id, "mox", new BlockPos(3, 2, 12));
        PonderText.build(scene, util, 11,1,3,14,4,9, Direction.DOWN);
        text(scene, util, id, "melter", new BlockPos(12, 1, 3));
        PonderText.build(scene, util, 15,1,3,15,3,3, Direction.DOWN);
        text(scene, util, id, "glass", new BlockPos(15, 2, 3));
        text(scene, util, id, "stop", new BlockPos(12, 1, 3));scene.markAsFinished();
    }
    public static void maintenance(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "fusion_maintenance";var scene = begin(builder, id, 17, .15f);scene.setSceneOffsetY(-6);
        for (var cell : net.createplant.fusion.FusionStructure.cells(new BlockPos(8, 5, 3), Direction.SOUTH)) scene.world().setBlock(cell.pos(), cell.expected(), false);
        PonderText.build(scene, util, 3,1,3,13,8,13, Direction.DOWN);
        text(scene, util, id, "reactor", new BlockPos(8, 5, 3));
        PonderText.build(scene, util, 2,1,2,14,13,14, Direction.DOWN);
        PonderText.build(scene, util, 0,14,2,14,15,14, Direction.DOWN);
        fusionHoist(scene, util, 2, 4, 0, 0);
        text(scene, util, id, "build", new BlockPos(8, 14, 8));
        text(scene, util, id, "safe", new BlockPos(8, 5, 3));
        text(scene, util, id, "supply", new BlockPos(1, 14, 2));
        scene.world().setKineticSpeed(util.select().fromTo(1, 14, 2, 14, 14, 14), 64);
        fusionHoist(scene, util, 6, 2, 5, 0);
        text(scene, util, id, "exchange", new BlockPos(8, 8, 4));
        fusionHoist(scene, util, 6, 2, 5, 1);
        text(scene, util, id, "damage", new BlockPos(1, 14, 2));
        scene.overlay().showControls(util.vector().centerOf(new BlockPos(1, 14, 2)), Pointing.RIGHT, 70).withItem(new net.minecraft.world.item.ItemStack(net.createplant.registry.CEFusion.IRRADIATION_CARTRIDGE.get()));
        text(scene, util, id, "irradiation", new BlockPos(1, 14, 2));scene.markAsFinished();
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
