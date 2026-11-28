package net.createplant.logistics.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.logistics.block.entity.IndustrialArmBlockEntity;
import net.createplant.logistics.item.ArmToolItem;
import net.createplant.logistics.registry.CEArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The industrial arm. With nothing fitted it works with its own claw; use a welding torch on it to fit the torch (an old one is
 * swapped out), an empty hand to take it off. Hoppers, funnels and belts can feed and empty the tool slot.
 */
public class IndustrialArmBlock extends KineticBlock implements IBE<IndustrialArmBlockEntity>, ICogWheel {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    /** Where the claw rests: the way the placing player looked. */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public IndustrialArmBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(net.minecraft.world.level.LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof ArmToolItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IndustrialArmBlockEntity arm) {
            ItemStack old = arm.tool().copy();
            arm.toolSlot.setStackInSlot(0, stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!old.isEmpty()) player.getInventory().placeItemBackInInventory(old);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof IndustrialArmBlockEntity arm) || arm.tool().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack old = arm.tool().copy();
            arm.toolSlot.setStackInSlot(0, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(old);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof IndustrialArmBlockEntity arm) arm.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public Class<IndustrialArmBlockEntity> getBlockEntityClass() {
        return IndustrialArmBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IndustrialArmBlockEntity> getBlockEntityType() {
        return CEArm.ARM_ENTITY.get();
    }
}
