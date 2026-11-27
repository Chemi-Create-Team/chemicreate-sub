package net.createplant.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Carries the stirring load of the tank below it, with no load when detached. */
public class VesselStirrerBlockEntity extends KineticBlockEntity {
    public VesselStirrerBlockEntity(BlockPos pos, BlockState state) { super(CEReactor.STIRRER_ENTITY.get(), pos, state); }

    /** The stainless tank whose roof this drive is centred on. */
    public LiddedVesselBlockEntity vessel() {
        if (level == null || !(level.getBlockEntity(worldPosition.below()) instanceof LiddedVesselBlockEntity part)) return null;
        var owner = part.getControllerBE();
        return owner != null && owner.module() == ReactorModule.STIRRED && worldPosition.equals(owner.stirrerPos()) ? owner : null;
    }

    @Override public float calculateStressApplied() {
        return lastStressApplied = vessel() == null ? 0 : ReactorModule.STIRRED.stress * LiddedVesselBlockEntity.IMPELLER_LAYERS;
    }

    /** Called when the tank below it starts or stops stirring. */
    public void refreshStress() {
        if (level != null && !level.isClientSide && hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
    }
}
