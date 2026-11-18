package net.createplant.block.entity;

import net.createplant.registry.CETransmission;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TransmissionTowerBlockEntity extends BlockEntity {
    public TransmissionTowerBlockEntity(BlockPos pos, BlockState state) {
        super(CETransmission.TOWER_ENTITY.get(), pos, state);
    }
}
