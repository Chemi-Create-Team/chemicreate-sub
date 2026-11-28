package net.createplant.atomics.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** The rotation input of a vault: the core reads its speed for the integrated cap drives and loads it with their stress. */
public class ShieldDriveBlockEntity extends KineticBlockEntity {
    private static final float PER_ROD = 4;
    private int rods;

    public ShieldDriveBlockEntity(BlockPos pos, BlockState state) { super(CENuclear.SHIELD_DRIVE_ENTITY.get(), pos, state); }

    /** Called by the core when it looks over its vault: how many integrated rod drives this input turns. */
    public void load(int count) {
        if (count == rods) return;
        rods = count;
        if (hasNetwork()) getOrCreateNetwork().updateStressFor(this, calculateStressApplied());
        setChanged();
    }

    @Override public float calculateStressApplied() {
        float impact = PER_ROD * rods;
        lastStressApplied = impact;
        return impact;
    }
}
