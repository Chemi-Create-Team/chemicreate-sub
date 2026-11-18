package net.createplant.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.AllBlocks;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createplant.block.HighPressureTurbineEndBlock;
import net.createplant.block.HighPressureTurbinePartBlock;
import net.createplant.block.SteamTurbineBlock;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public class ElectrolysisPonderScenes {

    public static void turbineMultiblock(SceneBuilder builder,SceneBuildingUtil util) {
        TurbineUnitPonderScenes.units(builder,util);
    }

    private static net.createmod.ponder.api.scene.Selection selectLine(SceneBuildingUtil util, BlockPos start,
        int count) {
        var selection = util.select().position(start);
        for (int i = 1; i < count; i++) {
            selection = selection.add(util.select().position(start.relative(Direction.EAST, i)));
        }
        return selection;
    }

    private static void placeShaftLine(CreateSceneBuilder scene, BlockPos start, int count, BlockState shaftState) {
        for (int i = 0; i < count; i++) {
            scene.world().setBlock(start.relative(Direction.EAST, i), shaftState, true);
        }
    }

    private static net.createmod.ponder.api.scene.Selection setSteamPipeNetwork(CreateSceneBuilder scene,
        SceneBuildingUtil util) {
        net.createmod.ponder.api.scene.Selection selection = null;
        selection=addSteamPipe(scene,util,selection,util.grid().at(3,6,6),pipeState(Direction.UP,Direction.DOWN));
        selection=addSteamPipe(scene,util,selection,util.grid().at(3,7,6),pipeState(Direction.DOWN,Direction.EAST));
        for(int x=4;x<=9;x++)selection=addSteamPipe(scene,util,selection,util.grid().at(x,7,6),pipeState(Direction.WEST,Direction.EAST));
        selection=addSteamPipe(scene,util,selection,util.grid().at(10,7,6),pipeState(Direction.WEST,Direction.DOWN));
        for(int y:new int[]{5,6})selection=addSteamPipe(scene,util,selection,util.grid().at(10,y,6),pipeState(Direction.UP,Direction.DOWN));
        scene.world().setBlock(util.grid().at(6,7,6),AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),true);
        scene.world().setBlock(util.grid().at(6,8,6),AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS,Direction.Axis.X),true);
        scene.world().setBlock(util.grid().at(7,8,6),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST),true);
        selection=selection.add(util.select().fromTo(6,8,6,7,8,6));
        scene.world().setKineticSpeed(util.select().fromTo(6,7,6,7,8,6),64);
        return selection;
    }

    private static net.createmod.ponder.api.scene.Selection addSteamPipe(CreateSceneBuilder scene,
        SceneBuildingUtil util, net.createmod.ponder.api.scene.Selection selection, BlockPos pos,
        BlockState pipeState) {
        scene.world().setBlock(pos, pipeState, true);
        var pipeSelection = util.select().position(pos);
        return selection == null ? pipeSelection : selection.add(pipeSelection);
    }

    private static BlockState pipeState(Direction... connections) {
        BlockState state = net.createplant.registry.CETransport.STAINLESS_PIPE.get()
            .defaultBlockState()
            .setValue(BlockStateProperties.NORTH, false)
            .setValue(BlockStateProperties.SOUTH, false)
            .setValue(BlockStateProperties.WEST, false)
            .setValue(BlockStateProperties.EAST, false)
            .setValue(BlockStateProperties.UP, false)
            .setValue(BlockStateProperties.DOWN, true);
        for (Direction connection : connections) {
            state = switch (connection) {
                case NORTH -> state.setValue(BlockStateProperties.NORTH, true);
                case SOUTH -> state.setValue(BlockStateProperties.SOUTH, true);
                case WEST -> state.setValue(BlockStateProperties.WEST, true);
                case EAST -> state.setValue(BlockStateProperties.EAST, true);
                case UP -> state.setValue(BlockStateProperties.UP, true);
                case DOWN -> state.setValue(BlockStateProperties.DOWN, true);
            };
        }
        return state;
    }

    public static void completeThermalTurbine(CreateSceneBuilder scene,SceneBuildingUtil util){
        scene.world().setBlocks(util.select().fromTo(0,1,0,24,10,24),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),false);
        TurbineUnitPonderScenes.plant(scene,util);
    }
    private static void setCasingLine(CreateSceneBuilder scene, BlockPos start, int shaftCount, int radius) {
        for (int shaft = 0; shaft < shaftCount; shaft++) {
            BlockPos center = start.relative(Direction.EAST, shaft);
            for (int ringX = -radius; ringX <= radius; ringX++) {
                for (int ringY = -radius; ringY <= radius; ringY++) {
                    if (!isCasingPosition(radius, ringX, ringY)) {
                        continue;
                    }
                    scene.world().setBlock(HighPressureTurbinePartBlock.partPos(center, Direction.Axis.X, ringX, ringY),
                        HighPressureTurbinePartBlock.expectedCasingState(Direction.Axis.X, ringX, ringY), true);
                }
            }
        }
    }

    private static boolean isCasingPosition(int radius, int ringX, int ringY) {
        if (ringX == 0 && ringY == 0) {
            return false;
        }
        return radius == 1 || Math.max(Math.abs(ringX), Math.abs(ringY)) == radius;
    }

    private static void setTurbineEnd(CreateSceneBuilder scene, BlockPos center, boolean positive,
        boolean lowPressure, int radius) {
        for (int ringX = -1; ringX <= 1; ringX++) {
            if (radius == 2) {
                break;
            }
            for (int ringY = -1; ringY <= 1; ringY++) {
                setTurbineEndPart(scene, center, positive, lowPressure, ringX, ringY);
            }
        }
        if (radius == 2) {
            for (int ringX = -2; ringX <= 2; ringX++) {
                for (int ringY = -2; ringY <= 2; ringY++) {
                    setTurbineEndPart(scene, center, positive, true, ringX, ringY);
                }
            }
        }
    }

    private static void setTurbineEndPart(CreateSceneBuilder scene, BlockPos center, boolean positive,
        boolean lowPressure, int ringX, int ringY) {
        BlockPos partPos = HighPressureTurbineEndBlock.partPos(center, Direction.Axis.X, ringX, ringY);
        BlockState partState = (lowPressure
            ? CEBlocks.LOW_PRESSURE_TURBINE_END.get()
            : CEBlocks.HIGH_PRESSURE_TURBINE_END.get()).defaultBlockState()
            .setValue(HighPressureTurbineEndBlock.AXIS, Direction.Axis.X)
            .setValue(HighPressureTurbineEndBlock.RING_X,
                HighPressureTurbineEndBlock.ringStateValue(ringX))
            .setValue(HighPressureTurbineEndBlock.RING_Y,
                HighPressureTurbineEndBlock.ringStateValue(ringY))
            .setValue(HighPressureTurbineEndBlock.POSITIVE, positive);
        scene.world().setBlock(partPos, partState, true);
    }

    private static void redrawSteamTurbine(CreateSceneBuilder scene, net.createmod.ponder.api.scene.Selection selection) {
        scene.world().modifyBlockEntityNBT(selection, SteamTurbineBlockEntity.class, tag -> { }, true);
    }


}
