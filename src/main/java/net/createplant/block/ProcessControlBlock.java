package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ProcessControlBlockEntity;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ProcessControlBlock extends Block implements IBE<ProcessControlBlockEntity> {
    public enum Kind { PID, INSTRUMENT, ACTUATOR }
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty INDICATOR = IntegerProperty.create("indicator", 0, 15);
    public final Kind kind;
    public ProcessControlBlock(Properties properties, Kind kind) {
        super(properties);this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(INDICATOR, 0));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, kind == Kind.PID ? context.getHorizontalDirection().getOpposite() : context.getClickedFace());
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, INDICATOR); }
    @Override public Class<ProcessControlBlockEntity> getBlockEntityClass() { return ProcessControlBlockEntity.class; }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        if (kind == Kind.PID) return Block.box(3, 0, 3, 13, 16, 13);
        if (kind == Kind.ACTUATOR) return Block.box(2, 0, 2, 14, 14, 14);
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(3, 1, 7, 13, 15, 16);
            case SOUTH -> Block.box(3, 1, 0, 13, 15, 9);
            case EAST -> Block.box(0, 1, 3, 9, 15, 13);
            case WEST -> Block.box(7, 1, 3, 16, 15, 13);
            case UP -> Block.box(3, 0, 1, 13, 9, 15);
            case DOWN -> Block.box(3, 7, 1, 13, 16, 15);
        };
    }
    @Override public BlockEntityType<? extends ProcessControlBlockEntity> getBlockEntityType() { return CEPlantSystems.CONTROL_ENTITY.get(); }
    @Override protected boolean isSignalSource(BlockState state) { return kind != Kind.ACTUATOR; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return kind == Kind.PID && side == state.getValue(FACING).getOpposite() || kind == Kind.INSTRUMENT ? state.getValue(INDICATOR) : 0;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return kind != Kind.ACTUATOR; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return state.getValue(INDICATOR); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ProcessControlBlockEntity control) control.operate(player, hit);
        return InteractionResult.SUCCESS;
    }
}
