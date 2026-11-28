package net.createplant.atomics.block;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.atomics.factory.FactoryKind;
import net.createplant.atomics.registry.CEContinuousFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ContinuousFactoryBlock extends HorizontalKineticBlock implements IBE<ContinuousFactoryBlockEntity> {
    private final FactoryKind kind;
    public static final net.minecraft.world.level.block.state.properties.Property<Direction> FACING = HORIZONTAL_FACING;

    public ContinuousFactoryBlock(Properties props, FactoryKind kind) { super(props); this.kind = kind; }
    public FactoryKind kind() { return kind; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) {
        return kind != FactoryKind.MILL && side == Direction.DOWN;
    }
    @Override public Class<ContinuousFactoryBlockEntity> getBlockEntityClass() { return ContinuousFactoryBlockEntity.class; }
    @Override public BlockEntityType<ContinuousFactoryBlockEntity> getBlockEntityType() { return CEContinuousFactory.CONTROLLER_ENTITY.get(); }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ContinuousFactoryBlockEntity machine)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (AllItems.WRENCH.isIn(stack) && !player.isShiftKeyDown()) {
            if (!level.isClientSide) machine.recover(player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        var inlet = machine.inlet(hit.getDirection());
        if (inlet == null || stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack remainder = inlet.insertItem(0, stack, true);
        if (remainder.getCount() == stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            var left = inlet.insertItem(0, stack, false);
            if (!player.isCreative()) player.setItemInHand(hand, left);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof ContinuousFactoryBlockEntity machine)) return InteractionResult.PASS;
        if (!level.isClientSide) machine.recover(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void updateEntityAfterFallOn(BlockGetter world, Entity entity) {
        super.updateEntityAfterFallOn(world, entity);
        if (!(world instanceof Level level) || level.isClientSide || !(entity instanceof ItemEntity drop) || !drop.isAlive()) return;
        var pos = BlockPos.containing(drop.getX(), drop.getY() - .05, drop.getZ());
        if (!(level.getBlockEntity(pos) instanceof ContinuousFactoryBlockEntity machine)) return;
        var inlet = machine.inlet(Direction.UP);
        ItemStack remaining = inlet.insertItem(0, drop.getItem(), false);
        if (remaining.isEmpty()) drop.discard();
        else drop.setItem(remaining);
    }
}
