package net.createplant.block;

import com.simibubi.create.foundation.block.IBE;
import net.createplant.block.entity.ColumnRingBlockEntity;
import net.createplant.registry.CEReactor;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The ring of a large absorption tower storey. It holds a passive block entity because Create's pipes connect only to blocks that have one;
 * every port it shows belongs to the storey at its centre.
 */
public class LargeAbsorptionTowerRingBlock extends ColumnRingBlock implements IBE<ColumnRingBlockEntity> {
    public LargeAbsorptionTowerRingBlock(Properties properties) { super(properties); }

    @Override public Class<ColumnRingBlockEntity> getBlockEntityClass() { return ColumnRingBlockEntity.class; }

    @Override public BlockEntityType<? extends ColumnRingBlockEntity> getBlockEntityType() { return CEReactor.RING_ENTITY.get(); }
}
