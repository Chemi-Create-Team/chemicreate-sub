package net.createplant.block;

import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.createplant.nuclear.ReactorConsoleLink;
import net.createplant.registry.CECrane;
import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The drive and cab of a gantry crane, either for RBMK fuel and control rod service or for fusion blanket and divertor service.
 * The near rail runs forward from the drive and the far rail is built to its right; rotation passes along the drive's shaft.
 */
public class CraneDriveBlock extends AbstractEncasedShaftBlock implements IBE<CraneDriveBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final boolean fusion;

    public CraneDriveBlock(Properties properties, boolean fusion) {
        super(properties);
        this.fusion = fusion;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(FACING, Direction.SOUTH));
    }

    public boolean fusion() { return fusion; }

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

    @Override public Class<CraneDriveBlockEntity> getBlockEntityClass() { return CraneDriveBlockEntity.class; }
    @Override public BlockEntityType<? extends CraneDriveBlockEntity> getBlockEntityType() { return CECrane.DRIVE_ENTITY.get(); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(CENuclear.REACTOR_CONTROL_CONSOLE_ITEM.get()))
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide) ReactorConsoleLink.rememberCrane(stack, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!newState.is(state.getBlock())) withBlockEntityDo(level, pos, CraneDriveBlockEntity::dismantle);
        super.onRemove(state, level, pos, newState, moving);
    }
}
