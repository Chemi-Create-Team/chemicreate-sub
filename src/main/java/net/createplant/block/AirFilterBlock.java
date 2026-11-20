package net.createplant.block;

import net.createplant.block.entity.AirFilterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class AirFilterBlock extends Block implements EntityBlock {
    public AirFilterBlock(Properties properties) { super(properties); }

    @Override public AirFilterBlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirFilterBlockEntity(pos,state); }
}
