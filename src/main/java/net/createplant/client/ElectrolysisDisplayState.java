package net.createplant.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.createplant.block.entity.ElectrolysisControllerBlockEntity;
import net.minecraft.util.Mth;

/** Presentation only: all four panels and the electrolyte share one damped sample. */
final class ElectrolysisDisplayState {
    static final int VOLTS = 0, AMPS = 1, FEED = 2, LOAD = 3;
    private static final Map<ElectrolysisControllerBlockEntity, ElectrolysisDisplayState> STATES = new WeakHashMap<>();
    private final float[] values = new float[4];
    private float time = Float.NaN;

    static ElectrolysisDisplayState sample(ElectrolysisControllerBlockEntity owner, float time) {
        var state = STATES.computeIfAbsent(owner, key -> new ElectrolysisDisplayState());
        if (time == state.time) return state;
        float dt = time - state.time;
        float chase = !Float.isFinite(dt) || dt < 0 || dt > 100 ? 1 : 1 - (float) Math.exp(-Mth.clamp(dt, 0, 5) / 3.5f);
        int capacity = Math.max(1, owner.cells().size() * net.createplant.block.entity.ElectrolysisCellBlockEntity.CAPACITY);
        state.chase(VOLTS, (float) owner.volts() / 500, chase);
        state.chase(AMPS, (float) owner.amps() / 320, chase);
        state.chase(FEED, owner.stored(0) / (float) capacity, chase);
        state.chase(LOAD, owner.limit() / 100f, chase);
        state.time = time;
        return state;
    }

    private void chase(int index, float target, float amount) { values[index] += (Mth.clamp(target, 0, 1) - values[index]) * amount; }
    float value(int index) { return values[index]; }
}
