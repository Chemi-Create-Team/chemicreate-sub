package net.createplant.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.GasCentrifugeBlockEntity;
import net.createplant.registry.CEHighStressMachines;
import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

public final class GasCentrifugeBlock extends KineticBlock implements IBE<GasCentrifugeBlockEntity> {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
    public GasCentrifugeBlock(Properties properties) {
        super(properties);
        var state = defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER).setValue(ACTIVE, false);
        for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) if (property != PipeBlock.UP && property != PipeBlock.DOWN) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF, ACTIVE, PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST);
    }
    public static BlockPos lower(BlockState state, BlockPos pos) { return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context.getClickedPos().getY() < context.getLevel().getMaxBuildHeight() - 1
            && context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context) ? defaultBlockState() : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        GasCentrifugeBlockEntity.changed(level, pos);
    }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (side == (state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)
            && (!neighbour.is(this) || neighbour.getValue(HALF) == state.getValue(HALF))) return Blocks.AIR.defaultBlockState();
        if (level instanceof Level world && !world.isClientSide) GasCentrifugeBlockEntity.changed(world, lower(state, pos));
        return super.updateShape(state, side, neighbour, level, pos, other);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.LOWER && level.getBlockEntity(pos) instanceof GasCentrifugeBlockEntity be) be.release();
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER)
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        var body = Block.box(3, 0, 3, 13, 16, 13);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? Shapes.or(Block.box(0, 0, 0, 16, 3, 16), body) : body;
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) { return state.getValue(HALF) == DoubleBlockHalf.LOWER && side == Direction.DOWN; }
    @Override public Class<GasCentrifugeBlockEntity> getBlockEntityClass() { return GasCentrifugeBlockEntity.class; }
    @Override public BlockEntityType<? extends GasCentrifugeBlockEntity> getBlockEntityType() { return CEHighStressMachines.CENTRIFUGE_ENTITY.get(); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && type == CEHighStressMachines.CENTRIFUGE_ENTITY.get() ? IBE.super.getTicker(level, state, type) : null;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return state.getValue(HALF) == DoubleBlockHalf.LOWER ? getBlockEntityType().create(pos, state) : new net.createplant.block.entity.GasCentrifugePortBlockEntity(pos, state); }
}
