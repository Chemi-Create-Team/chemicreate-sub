package net.createplant.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.createplant.block.ExtractionColumnStructureBlock;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no state and never ticks; it lets pipes and goggles find the ports and readings that the ring hands on from its storey. */
public class ColumnRingBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public ColumnRingBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.RING_ENTITY.get(), pos, state); }

    public BlockEntity storey() {
        BlockPos centre = level == null ? null : ExtractionColumnStructureBlock.centre(level, worldPosition, getBlockState());
        return centre == null ? null : level.getBlockEntity(centre);
    }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        return storey() instanceof IHaveGoggleInformation goggles && goggles.addToGoggleTooltip(tooltip, sneaking);
    }
}
