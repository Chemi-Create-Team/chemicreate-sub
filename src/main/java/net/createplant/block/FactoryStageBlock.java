package net.createplant.block;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.registry.CEContinuousFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class FactoryStageBlock extends HorizontalKineticBlock implements IBE<KineticBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.Property<Direction> FACING = HORIZONTAL_FACING;

    public FactoryStageBlock(Properties properties) { super(properties); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction side) { return side == state.getValue(FACING); }
    @Override public Class<KineticBlockEntity> getBlockEntityClass() { return KineticBlockEntity.class; }
    @Override public BlockEntityType<KineticBlockEntity> getBlockEntityType() { return CEContinuousFactory.STAGE_ENTITY.get(); }
}
