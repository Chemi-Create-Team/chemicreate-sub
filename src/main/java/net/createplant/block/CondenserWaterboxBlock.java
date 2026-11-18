package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CondenserWaterboxBlockEntity;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CondenserWaterboxBlock extends Block implements IBE<CondenserWaterboxBlockEntity> {
    public CondenserWaterboxBlock(Properties properties) { super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.HORIZONTAL_FACING); }
    @Override public Class<CondenserWaterboxBlockEntity> getBlockEntityClass() { return CondenserWaterboxBlockEntity.class; }
    @Override public BlockEntityType<? extends CondenserWaterboxBlockEntity> getBlockEntityType() { return CEPlantSystems.CONDENSER_ENTITY.get(); }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!next.is(state.getBlock()) && level.getBlockEntity(pos) instanceof CondenserWaterboxBlockEntity be) be.release();
        super.onRemove(state, level, pos, next, moved);
    }
}
