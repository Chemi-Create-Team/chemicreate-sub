package net.createplant.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.TurbineUnit;
import net.createplant.block.entity.HighPressureTurbinePartBlockEntity;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEItems;
import net.createplant.registry.CETransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

public final class TurbineUnitPonderScenes {
    private static final String TEXT = "ponder.create_refinery_n_reactors.turbine_multiblock.";

    private TurbineUnitPonderScenes() {}

    public static List<TurbineUnit.Layout> layouts() {
        var result = new ArrayList<TurbineUnit.Layout>();
        for (var stage : TurbineUnit.Stage.values()) {
            int start = stage == TurbineUnit.Stage.HIGH ? 15 : 2;
            for (int x = start; x < start + stage.sections(); x++)
                result.add(new TurbineUnit.Layout(new BlockPos(x, 5, 10), Direction.Axis.X, stage));
        }
        return result;
    }

    public static void section(CreateSceneBuilder scene, TurbineUnit.Layout unit) {
        TurbineUnit.states(unit)
                .forEach(
                        (pos, state) -> {
                            scene.world().setBlock(pos, state, false);
                            if (pos.equals(unit.origin()))
                                scene.world()
                                        .modifyBlockEntity(
                                                pos,
                                                SteamTurbineBlockEntity.class,
                                                be -> be.setUnitLayout(unit));
                            else
                                scene.world()
                                        .modifyBlockEntity(
                                                pos,
                                                HighPressureTurbinePartBlockEntity.class,
                                                be -> be.setUnitLayout(unit));
                        });
    }

    public static void plant(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world()
                .setBlocks(
                        util.select().fromTo(15, 1, 9, 19, 3, 11),
                        Blocks.POLISHED_ANDESITE.defaultBlockState(),
                        false);
        for (var unit : layouts()) section(scene, unit);
        scene.world()
                .setBlocks(
                        util.select().fromTo(12, 5, 10, 14, 5, 10),
                        AllBlocks.SHAFT
                                .getDefaultState()
                                .setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                        false);
        var pipe = CETransport.STAINLESS_PIPE.get().defaultBlockState();
        for (int z = 12; z < 15; z++)
            scene.world()
                    .setBlock(
                            new BlockPos(17, 5, z),
                            pipe.setValue(BlockStateProperties.NORTH, true)
                                    .setValue(BlockStateProperties.SOUTH, true),
                            false);
        for (int x = 8; x <= 17; x++)
            scene.world()
                    .setBlock(
                            new BlockPos(x, 5, 15),
                            pipe.setValue(BlockStateProperties.EAST, true)
                                    .setValue(BlockStateProperties.WEST, true),
                            false);
        scene.world()
                .setBlock(
                        new BlockPos(17, 5, 15),
                        pipe.setValue(BlockStateProperties.NORTH, true)
                                .setValue(BlockStateProperties.WEST, true),
                        false);
        scene.world()
                .setBlock(
                        new BlockPos(8, 5, 15),
                        pipe.setValue(BlockStateProperties.NORTH, true)
                                .setValue(BlockStateProperties.EAST, true),
                        false);        var pump=new BlockPos(14,5,15);
        scene.world().setBlock(pump,AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),false);
        scene.world().setBlock(pump.above(),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),false);
        scene.world().setBlock(pump.above().east(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),false);

    }

    public static void units(SceneBuilder builder, SceneBuildingUtil util) {
        var scene = new CreateSceneBuilder(builder);
        scene.title(
                "turbine_multiblock",
                PonderText.title(TEXT + "title", "Build five HP and ten LP sections"));
        scene.configureBasePlate(0, 0, 25);
        scene.scaleSceneView(.31f);
        scene.world()
                .setBlocks(
                        util.select().fromTo(0, 1, 0, 24, 10, 24),
                        Blocks.AIR.defaultBlockState(),
                        false);
        scene.showBasePlate();
        var crafters = util.select().fromTo(2, 1, 2, 10, 9, 2);
        scene.world().setBlocks(crafters, AllBlocks.MECHANICAL_CRAFTER.getDefaultState(), false);
        scene.world().showSection(crafters, Direction.DOWN);
        scene.world().setKineticSpeed(crafters, 32);
        scene.overlay()
                .showControls(util.vector().of(6, 8, 2), Pointing.DOWN, 70)
                .withItem(CEItems.HIGH_PRESSURE_TURBINE_UNIT.toStack());
        PonderText.caption(scene, util.vector().of(6, 6, 2), TEXT + "factory", 80);
        scene.world().hideSection(crafters, Direction.UP);
        scene.idle(20);
        var first = layouts().getFirst();
        section(scene, first);
        scene.world()
                .setBlocks(
                        util.select().fromTo(15, 1, 9, 19, 3, 11),
                        Blocks.POLISHED_ANDESITE.defaultBlockState(),
                        false);
        scene.world().showSection(util.select().fromTo(15, 1, 9, 19, 6, 11), Direction.DOWN);
        PonderText.caption(scene, util.vector().of(15, 5, 10), TEXT + "place", 80);
        plant(scene, util);
        var bodies =
                util.select()
                        .fromTo(2, 1, 6, 11, 9, 14)
                        .add(util.select().fromTo(15, 4, 9, 19, 6, 11));
        scene.world().showSection(bodies, Direction.DOWN);
        scene.overlay().showControls(util.vector().of(11, 3, 14), Pointing.RIGHT, 60)
                .rightClick().withItem(CEItems.LOW_PRESSURE_TURBINE_UNIT.toStack());
        PonderText.caption(scene, util.vector().of(11, 3, 14), TEXT + "connect", 90);
        var shafts = util.select().fromTo(12, 5, 10, 14, 5, 10);
        scene.world().showSection(shafts, Direction.WEST);
        scene.world().showSection(util.select().fromTo(8, 5, 12, 17, 6, 15), Direction.UP);
        scene.world().setKineticSpeed(bodies.add(shafts).add(util.select().fromTo(14,5,15,15,6,15)),64);
        PonderText.caption(scene, util.vector().of(17, 5, 12), TEXT + "pipes", 95);
        PonderText.caption(scene, util.vector().of(8, 1, 10), TEXT + "recover", 85);
    }
}
