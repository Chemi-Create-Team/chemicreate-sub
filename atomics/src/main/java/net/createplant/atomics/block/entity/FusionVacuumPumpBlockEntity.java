package net.createplant.atomics.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.atomics.registry.CEFusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The torus pump's large rotor and cold-valve train place a fixed load on the Create kinetic network. */
public class FusionVacuumPumpBlockEntity extends KineticBlockEntity {
    public static final float STRESS = 128;
    public FusionVacuumPumpBlockEntity(BlockPos pos, BlockState state) { super(CEFusion.VACUUM_PUMP_ENTITY.get(), pos, state); }
    @Override public float calculateStressApplied() { lastStressApplied = STRESS; return STRESS; }
}
