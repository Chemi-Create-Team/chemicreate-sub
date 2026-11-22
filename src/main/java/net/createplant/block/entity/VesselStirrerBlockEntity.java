package net.createplant.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Carries the stirring load of the tank over it: nothing while no stirred tank stands on it. */
public class VesselStirrerBlockEntity extends KineticBlockEntity {
    public VesselStirrerBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.STIRRER_ENTITY.get(), pos, state); }

    /** The stainless tank this stirs: one whose bottom centre stands on it and that stirs with it. */
    public LiddedVesselBlockEntity vessel() {
        if (level == null || !(level.getBlockEntity(worldPosition.above()) instanceof LiddedVesselBlockEntity part)) return null;
        var owner = part.getControllerBE();
        return owner != null && owner.module() == ReactorModule.STIRRED && worldPosition.equals(owner.stirrerPos()) ? owner : null;
    }

    @Override public float calculateStressApplied() {
        return lastStressApplied = vessel() == null ? 0 : ReactorModule.STIRRED.stress * LiddedVesselBlockEntity.IMPELLER_LAYERS;
    }

    /** Called when the tank over it starts or stops stirring. */
    public void refreshStress() {
        if (level != null && !level.isClientSide && hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }
}
