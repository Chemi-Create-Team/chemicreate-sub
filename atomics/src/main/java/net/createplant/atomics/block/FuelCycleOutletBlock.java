package net.createplant.atomics.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.atomics.block.entity.FuelCycleOutletBlockEntity;
import net.createplant.atomics.registry.CEFuelCycle;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class FuelCycleOutletBlock extends ExtractionColumnSectionBlock implements IBE<FuelCycleOutletBlockEntity> {
    public static final IntegerProperty TANK = IntegerProperty.create("tank", 2, 5);
    public FuelCycleOutletBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(TANK, 2)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TANK); }
    @Override public Class<FuelCycleOutletBlockEntity> getBlockEntityClass() { return FuelCycleOutletBlockEntity.class; }
    @Override public BlockEntityType<? extends FuelCycleOutletBlockEntity> getBlockEntityType() { return CEFuelCycle.OUTLET_ENTITY.get(); }
}
