package net.createplant.block;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CryogenicColdBoxBlockEntity;
import net.createplant.registry.CEIsotopeGases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CryogenicColdBoxBlock extends HorizontalKineticBlock implements IBE<CryogenicColdBoxBlockEntity> {
    public CryogenicColdBoxBlock(Properties properties) { super(properties); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == state.getValue(HORIZONTAL_FACING); }
    @Override public Class<CryogenicColdBoxBlockEntity> getBlockEntityClass() { return CryogenicColdBoxBlockEntity.class; }
    @Override public BlockEntityType<? extends CryogenicColdBoxBlockEntity> getBlockEntityType() { return CEIsotopeGases.COLD_BOX_ENTITY.get(); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof CryogenicColdBoxBlockEntity box) box.vent();
        super.onRemove(state, level, pos, next, moving);
    }
}
