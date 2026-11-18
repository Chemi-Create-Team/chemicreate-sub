package net.createplant.block.entity;

import net.createplant.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no state: Create's pipes only connect to blocks that have a block entity, and the bundle's fluid port is a capability. */
public class FeedBundleBlockEntity extends BlockEntity {
    public FeedBundleBlockEntity(BlockPos pos, BlockState state) { super(CENuclear.FEED_BUNDLE_ENTITY.get(), pos, state); }
}
