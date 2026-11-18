package net.createplant.block.entity;

import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no state and never ticks; it lets pipes find the ports that the ring hands on from its storey. */
public class ColumnRingBlockEntity extends BlockEntity {
    public ColumnRingBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.RING_ENTITY.get(), pos, state); }
}
