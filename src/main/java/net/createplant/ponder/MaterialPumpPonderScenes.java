package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MaterialPumpPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.material_pump.";
    private static final BlockPos PUMP = new BlockPos(3, 1, 3);

    private MaterialPumpPonderScenes() { }

    public static void pump(SceneBuilder builder, SceneBuildingUtil util, String material) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("material_pump", PonderText.title(LANG + "title", "Pumps matching the pipe material"));
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        var pump = BuiltInRegistries.BLOCK.get(CreatePlant.location(material + "_pump"));
        var pipe = BuiltInRegistries.BLOCK.get(CreatePlant.location(material + "_pipe"));
        scene.world().setBlock(PUMP, pump.defaultBlockState().setValue(PumpBlock.FACING, Direction.EAST), false);
        scene.world().replaceBlocks(util.select().position(2, 1, 3).add(util.select().position(4, 1, 3)), pipe.defaultBlockState(), false);
        scene.world().modifyBlocks(util.select().position(2, 1, 3).add(util.select().position(4, 1, 3)), state ->
            state.setValue(FluidPipeBlock.EAST, true).setValue(FluidPipeBlock.WEST, true)
                .setValue(FluidPipeBlock.NORTH, false).setValue(FluidPipeBlock.SOUTH, false)
                .setValue(FluidPipeBlock.UP, false).setValue(FluidPipeBlock.DOWN, false), false);
        PonderText.build(scene, util, 1, 1, 3, 5, 1, 3, Direction.DOWN);
        caption(scene, util, PUMP, "connect");
        scene.world().showSection(util.select().fromTo(2, 1, 4, 3, 1, 4), Direction.DOWN);
        var drive = util.select().position(PUMP).add(util.select().fromTo(2, 1, 4, 3, 1, 4));
        scene.world().setKineticSpeed(drive, 64);
        scene.world().setKineticSpeed(util.select().position(PUMP), -64);
        caption(scene, util, PUMP.south(), "drive");
        scene.overlay().showControls(util.vector().topOf(PUMP), Pointing.DOWN, 60)
            .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.world().modifyBlock(PUMP, state -> state.setValue(PumpBlock.FACING, Direction.WEST), false);
        caption(scene, util, PUMP, "reverse");
        scene.world().setKineticSpeed(drive, 0);
        caption(scene, util, PUMP, "stop");
        if (material.equals("gas") || material.equals("large_gas")) {
            scene.world().setBlock(util.grid().at(1, 1, 3), AllBlocks.NOZZLE.getDefaultState().setValue(NozzleBlock.FACING, Direction.WEST), false);
            scene.world().modifyBlock(PUMP, state -> state.setValue(PumpBlock.FACING, Direction.EAST), false);
            scene.world().setKineticSpeed(drive, 64);
            scene.world().setKineticSpeed(util.select().position(PUMP), -64);
            caption(scene, util, PUMP.west(2), "intake");
        }
        scene.markAsFinished();
    }

    private static void caption(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String key) {
        PonderText.caption(scene, util.vector().centerOf(pos), LANG + key, 100);
    }
}
