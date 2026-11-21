package net.createplant.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;

/** A 2x2 horizontal tank four blocks long, then the burners that make it a boiler, then a pipe at its end. */
public final class HorizontalTankPonderScenes {
    private static final String LANG = "ponder.create_refinery_n_reactors.horizontal_tank.";

    private HorizontalTankPonderScenes() { }

    public static void tank(SceneBuilder builder, SceneBuildingUtil util) { tank(builder,util,false); }
    public static void stainless(SceneBuilder builder, SceneBuildingUtil util) { tank(builder,util,true); }

    private static void tank(SceneBuilder builder, SceneBuildingUtil util, boolean stainless) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(stainless ? "stainless_boiler" : "horizontal_tank", PonderText.title(LANG + (stainless ? "stainless_title" : "title"), stainless ? "Stainless-steel boiler" : "Horizontal fluid tank"));
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        // Create joins the cells on placement; the drawn tank is given the controller data that joining would write.
        BlockPos controller = util.grid().at(2, 2, 3);
        var tank = util.select().fromTo(2, 2, 3, 5, 3, 4);
        if (stainless) scene.world().modifyBlocks(tank,state->net.createplant.registry.CETransport.STAINLESS_BOILER.get().defaultBlockState()
            .setValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.BOTTOM,state.getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.BOTTOM))
            .setValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.TOP,state.getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.TOP)), false);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            BlockPos part = controller.offset(x, y, z);
            scene.world().modifyBlockEntityNBT(util.select().position(part), FluidTankBlockEntity.class, tag -> {
                if (part.equals(controller)) { tag.remove("Controller");tag.putInt("Size", 2);tag.putInt("Height", 4); }
                else tag.put("Controller", NbtUtils.writeBlockPos(controller));
            });
        }
        var burners = util.select().fromTo(2, 1, 3, 5, 1, 4);
        var feed = util.select().position(3, 2, 2);
        var pipe = util.select().fromTo(0, 2, 3, 1, 2, 3);
        scene.idle(10);

        scene.world().showSection(tank, Direction.DOWN);
        text(scene, util, util.grid().at(5, 3, 3), 130, "place");

        scene.overlay().showOutline(PonderPalette.OUTPUT, "tank", tank, 120);
        text(scene, util, util.grid().at(2, 3, 4), 120, "size");

        scene.world().modifyBlockEntity(controller,FluidTankBlockEntity.class,be->be.getTankInventory().setFluid(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,12000)));
        scene.world().showSection(feed, Direction.SOUTH);
        scene.world().showSection(burners, Direction.UP);
        scene.world().modifyBlocks(burners,state->state.setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.KINDLED),false);
        scene.world().modifyBlockEntityNBT(util.select().position(controller), FluidTankBlockEntity.class, tag -> {
            tag.putBoolean("TubeMode", true);
            tag.putInt("TubeGauge", 35);
        });
        scene.overlay().showOutline(PonderPalette.RED, "burners", burners, 120);
        text(scene, util, util.grid().at(3, 1, 3), 120, "boiler");

        text(scene, util, util.grid().at(4, 2, 4), 120, "heating");
        scene.world().showSection(pipe, Direction.EAST);
        text(scene, util, util.grid().at(1, 2, 3), 110, stainless ? "stainless_output" : "atmospheric_output");
        scene.overlay().showControls(util.vector().topOf(util.grid().at(4, 3, 3)), Pointing.DOWN, 100).withItem(AllItems.GOGGLES.asStack());
        text(scene, util, util.grid().at(1, 2, 3), 110, "pipe");
        scene.markAsFinished();
    }

    private static void text(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos target, int ticks, String key) {
        PonderText.caption(scene, util.vector().centerOf(target), LANG + key, ticks);
    }
}
