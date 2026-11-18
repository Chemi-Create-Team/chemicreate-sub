package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.CondenserCoolingPortBlockEntity;
import net.createplant.registry.CEPlantSystems;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CondenserCoolingPortBlock extends Block implements IBE<CondenserCoolingPortBlockEntity> {
    public final boolean input;
    public CondenserCoolingPortBlock(Properties properties, boolean input) {
        super(properties);this.input = input;registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.HORIZONTAL_FACING); }
    @Override public Class<CondenserCoolingPortBlockEntity> getBlockEntityClass() { return CondenserCoolingPortBlockEntity.class; }
    @Override public BlockEntityType<? extends CondenserCoolingPortBlockEntity> getBlockEntityType() { return CEPlantSystems.CONDENSER_PORT_ENTITY.get(); }
}
