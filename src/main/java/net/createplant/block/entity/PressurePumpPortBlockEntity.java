package net.createplant.block.entity;

import net.createplant.registry.CEPressureInfrastructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Create's pipe discovery requires an actual block entity at a fluid capability endpoint. */
public final class PressurePumpPortBlockEntity extends BlockEntity {
    public PressurePumpPortBlockEntity(BlockPos pos, BlockState state) {
        super(CEPressureInfrastructure.PUMP_PORT_ENTITY.get(), pos, state);
    }
}
