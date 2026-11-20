package net.createplant.ponder;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.*;
import net.createplant.block.GasifierBlock;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.CEProcessFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;

public final class GasifierPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.entrained_gasifier.";
    private GasifierPonderScenes() { }

    public static void processing(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("entrained_gasifier", PonderText.title(LANG + "title", "Steam gasifier"));
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(.6f);
        scene.setSceneOffsetY(-1);
        scene.showBasePlate();
        var heater = new BlockPos(5, 1, 5);
        var controller = heater.above();
        scene.world().showSection(util.select().position(heater), Direction.DOWN);
        caption(scene, util, heater, "heater");
        scene.overlay().showControls(util.vector().topOf(heater), Pointing.DOWN, 55)
            .rightClick().withItem(CEProcessFluids.WATER_GAS.get().getBucket().getDefaultInstance());
        scene.world().modifyBlock(heater, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL,
            BlazeBurnerBlock.HeatLevel.SEETHING), false);
        caption(scene, util, heater, "fuel_bucket");
        var fuelSupply = util.select().fromTo(5, 1, 2, 6, 1, 4);
        scene.world().showSection(fuelSupply, Direction.NORTH);
        scene.world().setKineticSpeed(fuelSupply, 32);
        scene.world().setKineticSpeed(util.select().position(6, 1, 3), -32);
        caption(scene, util, heater.north(), "fuel_pipe");

        cells(scene, false);
        PonderText.build(scene, util, 4, 2, 4, 6, 7, 6, Direction.DOWN);
        caption(scene, util, new BlockPos(5, 5, 4), "base");
        cells(scene, true);
        scene.idle(10);
        caption(scene, util, new BlockPos(5, 5, 4), "layers");
        scene.world().showSection(util.select().fromTo(2, 2, 5, 3, 2, 5), Direction.EAST);
        caption(scene, util, new BlockPos(3, 2, 5), "steam");
        scene.world().showSection(util.select().position(4, 8, 4), Direction.DOWN);
        scene.overlay().showControls(util.vector().topOf(4, 8, 4), Pointing.DOWN, 50).withItem(Items.COAL.getDefaultInstance());
        caption(scene, util, new BlockPos(4, 8, 4), "feed");

        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("preheat", 600));
        caption(scene, util, controller, "preheat");
        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("running", 1000));
        glow(scene, util, true);
        caption(scene, util, new BlockPos(5, 4, 4), "glow");
        var pumpDrive = util.select().fromTo(6, 8, 6, 6, 9, 6);
        scene.world().showSection(util.select().fromTo(5, 8, 6, 6, 9, 6), Direction.DOWN);
        scene.world().setKineticSpeed(pumpDrive, 32);
        scene.world().setKineticSpeed(util.select().position(5, 9, 6), -32);
        caption(scene, util, new BlockPos(5, 8, 6), "output");
        caption(scene, util, new BlockPos(5, 8, 6), "syngas");
        scene.world().setKineticSpeed(fuelSupply, 0);
        scene.world().modifyBlock(heater, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL,
            BlazeBurnerBlock.HeatLevel.SMOULDERING), false);
        scene.world().modifyBlockEntity(controller, GasifierBlockEntity.class, be -> be.preview("heat_required", 700));
        glow(scene, util, false);
        caption(scene, util, heater, "stop");
        scene.markAsFinished();
    }

    /** Shows the furnace's blocks loose, or as the placed furnace forms them. */
    private static void cells(CreateSceneBuilder scene, boolean formed) {
        for (int layer = 0; layer < 6; layer++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int cell = formed ? GasifierBlock.cell(layer, dx, dz) : 0;
            scene.world().modifyBlock(new BlockPos(5 + dx, 2 + layer, 5 + dz), state -> state.setValue(GasifierBlock.CELL, cell), false);
        }
    }

    private static void glow(CreateSceneBuilder scene, SceneBuildingUtil util, boolean lit) {
        for (int layer = 2; layer <= 3; layer++) for (var side : Direction.Plane.HORIZONTAL)
            scene.world().modifyBlock(new BlockPos(5, 2 + layer, 5).relative(side), state -> state.setValue(GasifierBlock.LIT, lit), false);
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 130);
    }
}
