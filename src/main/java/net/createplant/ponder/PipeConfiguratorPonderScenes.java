package net.createplant.ponder;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createplant.registry.CEPortableTools;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PipeConfiguratorPonderScenes {
    private static final String KEY = "ponder.create_refinery_n_reactors.pipe_configurator.";
    private static final BlockPos TEE = new BlockPos(2, 1, 2);
    private static final AABB ARM = new AABB(2.75, 1.25, 2.25, 3, 1.75, 2.75);
    private static final Vec3 JOINT = new Vec3(2.9, 1.65, 2.55);

    private PipeConfiguratorPonderScenes() { }

    public static void tool(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title("pipe_configurator", PonderText.title(KEY + "title", "Isolating a pipe connection"));
        scene.configureBasePlate(0, 0, 5);
        scene.scaleSceneView(1.15f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 2, 4), Direction.DOWN);
        scene.idle(16);
        var tool = CEPortableTools.PIPE_CONFIGURATOR.toStack();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, "joint", ARM, 115);
        scene.overlay().showControls(JOINT, Pointing.RIGHT, 115).rightClick().withItem(tool);
        PonderText.caption(scene, JOINT, KEY + "select", 110);
        setJoint(scene, false);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, "joint", ARM, 115);
        PonderText.caption(scene, JOINT, KEY + "cut", 110);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.RED, "joint", ARM, 115);
        scene.overlay().showControls(JOINT, Pointing.RIGHT, 115).rightClick().withItem(tool);
        PonderText.caption(scene, JOINT, KEY + "restore", 110);
        setJoint(scene, true);
        scene.idle(24);
        scene.markAsFinished();
    }

    private static void setJoint(CreateSceneBuilder scene, boolean open) {
        scene.world().modifyBlock(TEE, state -> state.setValue(FluidPipeBlock.EAST, open), false);
        scene.world().modifyBlock(TEE.east(), state -> state.setValue(FluidPipeBlock.WEST, open), false);
    }
}
