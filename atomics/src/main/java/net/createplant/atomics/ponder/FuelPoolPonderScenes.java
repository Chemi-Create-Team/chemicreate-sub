package net.createplant.atomics.ponder;

import net.createplant.ponder.PonderText;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class FuelPoolPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.fuel_pool.";

    private FuelPoolPonderScenes() { }

    public static void pool(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("fuel_pool", PonderText.title(LANG + "title", "An open spent fuel pool"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.17f);
        scene.setSceneOffsetY(-8);
        scene.showBasePlate();
        var racks = util.select().fromTo(3, 4, 3, 6, 4, 6);
        scene.world().showSection(racks, Direction.DOWN);
        scene.world().showSection(util.select().fromTo(2, 4, 7, 7, 31, 7).add(util.select().fromTo(7, 4, 2, 7, 31, 6)), Direction.DOWN);
        text(scene, util, new BlockPos(5, 4, 5), "structure");
        PonderText.build(scene, util, 1, 1, 3, 6, 3, 4, Direction.UP);
        scene.world().setKineticSpeed(util.select().fromTo(1, 1, 3, 6, 3, 4), 32);
        scene.world().modifyBlockEntityNBT(racks, FuelPoolBlockEntity.class, tag -> {
            tag.putInt("PoolCells", 16);
            tag.putInt("PoolAmount", 448000);
            tag.putString("PoolStatus", "ready");
        });
        text(scene, util, new BlockPos(3, 1, 3), "water");
        scene.world().modifyBlockEntityNBT(util.select().position(4, 4, 4), FuelPoolBlockEntity.class,
            tag -> tag.putByteArray("Slots", new byte[] {2, 2, 1, 0}));
        text(scene, util, new BlockPos(4, 30, 4), "rods");
        text(scene, util, new BlockPos(5, 1, 3), "cooling");
        text(scene, util, new BlockPos(4, 4, 4), "display");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 140);
    }
}
