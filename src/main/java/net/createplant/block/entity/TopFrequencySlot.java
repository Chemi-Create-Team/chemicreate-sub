package net.createplant.block.entity;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

/** The two frequency slots of a control rod link or a rod's cap, side by side on its top face. */
public class TopFrequencySlot extends ValueBoxTransform.Sided {
    private final boolean first;

    private TopFrequencySlot(boolean first) { this.first = first; }

    public static Pair<ValueBoxTransform, ValueBoxTransform> pair() { return Pair.of(new TopFrequencySlot(true), new TopFrequencySlot(false)); }

    @Override protected Vec3 getSouthLocation() { return new Vec3(first ? 5 : 11, 8, 15.5).scale(1 / 16d); }

    @Override public float getScale() { return .4f; }
}
