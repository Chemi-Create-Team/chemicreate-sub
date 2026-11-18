package net.createplant.block;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CubicPressBlockEntity;
import net.createplant.registry.CEHighStressMachines;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class CubicPressBlock extends HorizontalAxisKineticBlock implements IBE<CubicPressBlockEntity> {
    public CubicPressBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_AXIS); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) { return side.getAxis() == getRotationAxis(state); }
    @Override public Class<CubicPressBlockEntity> getBlockEntityClass() { return CubicPressBlockEntity.class; }
    @Override public BlockEntityType<? extends CubicPressBlockEntity> getBlockEntityType() { return CEHighStressMachines.PRESS_ENTITY.get(); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CubicPressBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < 2; slot++) remaining = be.port().insertItem(slot, remaining, false);
            if (remaining.getCount() == stack.getCount()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!player.isCreative()) stack.setCount(remaining.getCount());
        }
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof CubicPressBlockEntity be && !level.isClientSide)
            player.getInventory().placeItemBackInInventory(be.port().extractItem(2, 64, false));
        return InteractionResult.SUCCESS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof CubicPressBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, next, moving);
    }
}
