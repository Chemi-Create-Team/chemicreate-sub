package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.relay.CERelayComputer;
import net.createplant.relay.RelayRackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The relay computer multiblock taking over a reactor. */
public final class RelayPonderScenes {
    private RelayPonderScenes() { }

    private static void stage(CreateSceneBuilder scene, SceneBuildingUtil util, String id) {
        scene.title(id, PonderText.title("ponder.create_refinery_n_reactors." + id + ".title", id));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.75f);
        scene.world().replaceBlocks(util.select().fromTo(0, 1, 0, 8, 8, 8), Blocks.AIR.defaultBlockState(), false);
        scene.showBasePlate();
        scene.idle(10);
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos at, String id, String key) {
        PonderText.caption(scene, util.vector().centerOf(at), "ponder.create_refinery_n_reactors." + id + "." + key, 100);
    }

    public static void computer(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        String id = "relay_computer";
        stage(scene, util, id);
        BlockPos base = new BlockPos(4, 1, 4);
        scene.world().setBlock(base, CERelayComputer.COMPUTER.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH), false);
        scene.world().showSection(util.select().position(base), Direction.DOWN);
        text(scene, util, base, id, "base");
        for (int i = 1; i <= 2; i++) {
            scene.world().setBlock(base.above(i), CERelayComputer.RACK.get().defaultBlockState(), false);
            scene.world().showSection(util.select().position(base.above(i)), Direction.DOWN);
            scene.idle(8);
        }
        text(scene, util, base.above(2), id, "racks");
        scene.world().cycleBlockProperty(base, RelayRackBlock.ACTIVE);
        for (int i = 1; i <= 2; i++) scene.world().cycleBlockProperty(base.above(i), RelayRackBlock.ACTIVE);
        text(scene, util, base, id, "range");
        scene.overlay().showControls(util.vector().blockSurface(base, Direction.NORTH), Pointing.RIGHT, 50).rightClick();
        text(scene, util, base, id, "target");
        text(scene, util, base, id, "fission");
        text(scene, util, base, id, "fusion");
        scene.overlay().showControls(util.vector().blockSurface(base, Direction.NORTH), Pointing.RIGHT, 40).withItem(new ItemStack(Items.LEVER));
        scene.world().cycleBlockProperty(base, RelayRackBlock.ACTIVE);
        for (int i = 1; i <= 2; i++) scene.world().cycleBlockProperty(base.above(i), RelayRackBlock.ACTIVE);
        text(scene, util, base, id, "manual");
    }
}
