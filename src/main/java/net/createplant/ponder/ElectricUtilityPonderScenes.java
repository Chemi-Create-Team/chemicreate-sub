package net.createplant.ponder;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.ponder.WireConnectionInstructions;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.ElectricPumpBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ElectricUtilityPonderScenes {
    private static final BlockPos MACHINE = new BlockPos(4, 1, 4), BATTERY = new BlockPos(4, 1, 7);

    private ElectricUtilityPonderScenes() {}

    public static void pump(SceneBuilder builder, SceneBuildingUtil util) {
        String id = "electric_pump";var scene = new CreateSceneBuilder(builder);
        scene.title(id, PonderText.title("ponder.create_refinery_n_reactors." + id + ".title", id));
        scene.configureBasePlate(0, 0, 9);scene.scaleSceneView(.8f);scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(1, 1, 3, 7, 2, 5), Direction.DOWN);caption(scene, util, id, "place");
        scene.world().showSection(util.select().position(BATTERY), Direction.DOWN);
        var wires = new WireConnectionInstructions(scene);
        for (int node = 0; node < 2; node++) wires.createConnection(new InWorldNode(node, BATTERY), new InWorldNode(node, MACHINE));
        caption(scene, util, id, "wire");scene.world().setKineticSpeed(util.select().position(MACHINE), 124);
        fill(scene, new BlockPos(1, 1, 4), 4000);scene.idle(30);fill(scene, new BlockPos(1, 1, 4), 0);fill(scene, new BlockPos(7, 1, 4), 4000);
        scene.overlay().showControls(util.vector().centerOf(MACHINE), Pointing.RIGHT, 60).withItem(AllItems.WRENCH.asStack()).rightClick();scene.idle(20);
        scene.world().modifyBlock(MACHINE, state -> state.setValue(ElectricPumpBlock.FACING, Direction.WEST), false);
        caption(scene, util, id, "heat");scene.idle(40);scene.world().setKineticSpeed(util.select().position(MACHINE), 0);
        caption(scene, util, id, "stop");scene.markAsFinished();
    }

    private static void caption(
            CreateSceneBuilder scene, SceneBuildingUtil util, String id, String key) {
        PonderText.caption(
                scene,
                util.vector().centerOf(MACHINE),
                "ponder.create_refinery_n_reactors." + id + "." + key,
                100);
    }

    private static void fill(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world()
                .modifyBlockEntity(
                        pos,
                        FluidTankBlockEntity.class,
                        tank ->
                                tank.getTankInventory()
                                        .setFluid(
                                                amount == 0
                                                        ? FluidStack.EMPTY
                                                        : new FluidStack(Fluids.WATER, amount)));
    }
}
