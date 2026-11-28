package net.createplant.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.registry.CEQuarry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Quarry corner drive with two native range settings on its outward faces. */
public class QuarryDriveBlock extends AbstractEncasedShaftBlock implements IBE<QuarryDriveBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public QuarryDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(FACING, Direction.SOUTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing).setValue(AXIS, facing.getAxis());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = rotation.rotate(state.getValue(FACING));
        return state.setValue(FACING, facing).setValue(AXIS, facing.getAxis());
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockState getRotatedBlockState(BlockState state, Direction face) {
        return face.getAxis().isVertical() ? rotate(state, Rotation.CLOCKWISE_90) : state;
    }

    @Override public Class<QuarryDriveBlockEntity> getBlockEntityClass() { return QuarryDriveBlockEntity.class; }
    @Override public BlockEntityType<? extends QuarryDriveBlockEntity> getBlockEntityType() { return CEQuarry.QUARRY_DRIVE_ENTITY.get(); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!AllBlocks.MECHANICAL_DRILL.isIn(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        return onBlockEntityUseItemOn(level, pos, quarry -> {
            if (!quarry.fitDrill()) return ItemInteractionResult.FAIL;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return onBlockEntityUse(level, pos, quarry -> {
            if (!quarry.removeDrill()) return InteractionResult.PASS;
            player.getInventory().placeItemBackInInventory(AllBlocks.MECHANICAL_DRILL.asStack());
            return InteractionResult.SUCCESS;
        });
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(state.getBlock())) withBlockEntityDo(level, pos, QuarryDriveBlockEntity::dismantle);
        super.onRemove(state, level, pos, newState, moving);
    }
}
