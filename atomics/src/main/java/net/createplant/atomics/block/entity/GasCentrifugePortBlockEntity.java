package net.createplant.atomics.block.entity;

import java.util.List;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.createplant.atomics.registry.CEHighStressMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Native Create pipes discover a port through its block entity. The lower half owns all contents. */
public final class GasCentrifugePortBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public GasCentrifugePortBlockEntity(BlockPos pos, BlockState state) { super(CEHighStressMachines.CENTRIFUGE_PORT.get(), pos, state); }
    public GasCentrifugeBlockEntity owner() { return level == null ? null : GasCentrifugeBlockEntity.at(level, worldPosition.below()); }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) { var owner = owner();return owner != null && owner.addToGoggleTooltip(lines, sneaking); }
}
