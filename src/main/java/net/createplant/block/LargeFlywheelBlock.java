package net.createplant.block;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;

import net.createplant.block.entity.LargeFlywheelBlockEntity;
import net.createplant.registry.CEBlockEntityTypes;
import net.createplant.registry.CEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LargeFlywheelBlock extends RotatedPillarKineticBlock implements IBE<LargeFlywheelBlockEntity> {
    private static final VoxelShape X_AXIS = box(-2, -36, -36, 18, 52, 52);
    private static final VoxelShape Y_AXIS = box(-36, -2, -36, 52, 18, 52);
    private static final VoxelShape Z_AXIS = box(-36, -36, -2, 52, 52, 18);

    public LargeFlywheelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<LargeFlywheelBlockEntity> getBlockEntityClass() {
        return LargeFlywheelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LargeFlywheelBlockEntity> getBlockEntityType() {
        return CEBlockEntityTypes.LARGE_FLYWHEEL.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X_AXIS;
            case Y -> Y_AXIS;
            case Z -> Z_AXIS;
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == getRotationAxis(state);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        LargeFlywheelBlock dyedBlock = CEBlocks.largeFlywheelForColor(dye.getDyeColor());
        if (state.is(dyedBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            BlockState dyedState = dyedBlock.defaultBlockState()
                .setValue(AXIS, state.getValue(AXIS));
            KineticBlockEntity.switchToBlockState(level, pos, dyedState);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!(newState.getBlock() instanceof LargeFlywheelBlock)
            && level.getBlockEntity(pos) instanceof LargeFlywheelBlockEntity flywheel) {
            flywheel.discardStoredEnergy();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public float getParticleTargetRadius() {
        return 4;
    }

    @Override
    public float getParticleInitialRadius() {
        return 3.5f;
    }
}
