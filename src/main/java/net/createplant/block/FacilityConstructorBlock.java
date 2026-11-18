package net.createplant.block;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlock;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.createplant.block.entity.FacilityConstructorBlockEntity;
import net.createplant.registry.CEAssembly;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class FacilityConstructorBlock extends SchematicannonBlock implements IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public FacilityConstructorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection()); }
    @Override public BlockEntityType<? extends SchematicannonBlockEntity> getBlockEntityType() { return CEAssembly.CONSTRUCTOR_ENTITY.get(); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        withBlockEntityDo(level, pos, be -> be.defaultYaw = -state.getValue(FACING).toYRot());
    }

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FacilityConstructorBlockEntity be) || !be.canConfigure())
            return InteractionResult.FAIL;
        BlockState rotated = state.rotate(Rotation.CLOCKWISE_90);
        if (!context.getLevel().isClientSide) {
            context.getLevel().setBlock(context.getClickedPos(), rotated, 3);
            be.planChanged();
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof FacilityConstructorBlockEntity be && !be.flyingBlocks.isEmpty())
            return InteractionResult.FAIL;
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(state.getBlock()) && level.getBlockEntity(pos) instanceof FacilityConstructorBlockEntity be) {
            be.inventory.setStackInSlot(0, ItemStack.EMPTY);
            be.inventory.setStackInSlot(1, ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, next, moving);
    }
}
