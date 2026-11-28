package net.createplant.atomics.block;

import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;

import net.createplant.atomics.block.entity.ConverterTrunnionBearingBlockEntity;
import net.createplant.atomics.registry.CEConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Powered bearing which accepts only the exact glued converter vessel. */
public class ConverterTrunnionBearingBlock extends BearingBlock implements IBE<ConverterTrunnionBearingBlockEntity> {
    public ConverterTrunnionBearingBlock(Properties properties) { super(properties); }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        IBE.onRemove(state, level, pos, next);
    }

    @Override public Class<ConverterTrunnionBearingBlockEntity> getBlockEntityClass() {
        return ConverterTrunnionBearingBlockEntity.class;
    }

    @Override public BlockEntityType<? extends ConverterTrunnionBearingBlockEntity> getBlockEntityType() {
        return CEConverter.TRUNNION_ENTITY.get();
    }
}
