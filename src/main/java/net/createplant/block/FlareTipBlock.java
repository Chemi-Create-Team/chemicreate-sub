package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.FlareTipBlockEntity;
import net.createplant.registry.CEFlare;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlareTipBlock extends Block implements IBE<FlareTipBlockEntity>,SimpleWaterloggedBlock {
    public static final BooleanProperty PILOT=BooleanProperty.create("pilot"),WATERLOGGED=BlockStateProperties.WATERLOGGED;

    public FlareTipBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(PILOT,false).setValue(WATERLOGGED,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(PILOT,WATERLOGGED); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }
    @Override protected FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state); }
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbour,LevelAccessor level,BlockPos pos,BlockPos next) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
        return super.updateShape(state,side,neighbour,level,pos,next);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return Block.box(1,0,1,15,16,15); }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(PILOT) || state.getValue(WATERLOGGED)) return ItemInteractionResult.CONSUME;
        if (!level.isClientSide) {
            level.setBlock(pos,state.setValue(PILOT,true),Block.UPDATE_ALL);
            level.playSound(null,pos,SoundEvents.FLINTANDSTEEL_USE,SoundSource.BLOCKS,1,1);
            if (!player.getAbilities().instabuild) {
                if (stack.is(Items.FLINT_AND_STEEL)) stack.hurtAndBreak(1,player,LivingEntity.getSlotForHand(hand));
                else stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if (!state.getValue(PILOT) || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos,state.setValue(PILOT,false),Block.UPDATE_ALL);
            level.playSound(null,pos,SoundEvents.FIRE_EXTINGUISH,SoundSource.BLOCKS,.5f,1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!moving && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof FlareTipBlockEntity tip) tip.releaseGas();
        IBE.onRemove(state,level,pos,next);super.onRemove(state,level,pos,next,moving);
    }
    @Override public Class<FlareTipBlockEntity> getBlockEntityClass() { return FlareTipBlockEntity.class; }
    @Override public BlockEntityType<? extends FlareTipBlockEntity> getBlockEntityType() { return CEFlare.TIP_ENTITY.get(); }
}
