package net.createplant.atomics.block;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.FuelCycleMachineBlockEntity;
import net.createplant.atomics.registry.CEFuelCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class FuelCycleMachineBlock extends HorizontalKineticBlock implements IBE<FuelCycleMachineBlockEntity> {
    public FuelCycleMachineBlock(Properties properties) { super(properties); }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }
    @Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) { return face == state.getValue(HORIZONTAL_FACING); }
    @Override public Class<FuelCycleMachineBlockEntity> getBlockEntityClass() { return FuelCycleMachineBlockEntity.class; }
    @Override public BlockEntityType<? extends FuelCycleMachineBlockEntity> getBlockEntityType() { return CEFuelCycle.MACHINE_ENTITY.get(); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock()) && level.getBlockEntity(pos) instanceof FuelCycleMachineBlockEntity be) be.release();
        super.onRemove(state, level, pos, next, moved);
    }
}
