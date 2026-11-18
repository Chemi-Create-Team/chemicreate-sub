package net.createplant.block;

import net.createplant.chemistry.ReactorModule;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

public class ThermalMachineBlock extends ReactorMachineBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public ThermalMachineBlock(Properties properties) { super(properties,ReactorModule.PREHEATER);registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH)); }
    @Override public boolean hasShaftTowards(net.minecraft.world.level.LevelReader level,net.minecraft.core.BlockPos pos,BlockState state,Direction side) {
        return false;
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) { super.createBlockStateDefinition(builder);builder.add(FACING); }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) { return net.minecraft.world.phys.shapes.Shapes.block(); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front=context.getHorizontalDirection();if (context.getPlayer()==null || !context.getPlayer().isShiftKeyDown()) front=front.getOpposite();
        return defaultBlockState().setValue(FACING,front).setValue(AXIS,front.getAxis());
    }
    @Override public BlockState rotate(BlockState state,net.minecraft.world.level.block.Rotation rotation) { Direction front=rotation.rotate(state.getValue(FACING));return state.setValue(FACING,front).setValue(AXIS,front.getAxis()); }
    @Override public BlockState mirror(BlockState state,net.minecraft.world.level.block.Mirror mirror) { return rotate(state,mirror.getRotation(state.getValue(FACING))); }
}
