package net.createplant.atomics.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.LargeMechanicalPressBlockEntity;
import net.createplant.atomics.registry.CELargePress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LargeMechanicalPressBlock extends KineticBlock implements IBE<LargeMechanicalPressBlockEntity> {
    public LargeMechanicalPressBlock(Properties properties) { super(properties.pushReaction(PushReaction.BLOCK)); }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }

    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) { return side == Direction.UP; }

    @Override public Class<LargeMechanicalPressBlockEntity> getBlockEntityClass() { return LargeMechanicalPressBlockEntity.class; }
    @Override public BlockEntityType<LargeMechanicalPressBlockEntity> getBlockEntityType() { return CELargePress.PRESS_ENTITY.get(); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return LargePressFootprint.canPlace(context, false) ? defaultBlockState() : null;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LargePressFrameBlock.BEAM;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LargePressFootprint.place(level, pos, false);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this)) LargePressFootprint.remove(level, pos, false, moving);
        super.onRemove(state, level, pos, next, moving);
    }
}
