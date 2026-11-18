package net.createplant.nuclear;

import net.minecraft.nbt.CompoundTag;

/** Server-authoritative pressure relief and deterministic client motion for the four 350 kg RBMK channel plugs in a cap. */
public final class PlugPhysicsSimulator {
    public static final int PLUGS = ReactorPhysics.RODS_PER_BLOCK;
    public static final double PLUG_MASS_KG = 350;
    public static final float RELIEF_OPEN_METRES = .05f, MAX_LIFT_METRES = .35f;
    private static final double WARM_KELVIN = ReactorPhysics.SATURATION_KELVIN - 20, WARM_RANGE = 20, MAX_HEAT = 2, PRESSURE_THRESHOLD = .85, FLOW_REFERENCE = 64;
    private static final double ROD_HEAT_REDUCTION = .65, LIFT_AMPLITUDE = .18, NOISE_AMPLITUDE = .24;
    private static final double RELIEF_DECAY = .012, RELIEF_BASE = .08, RELIEF_LIFT = .18;
    private static final int SYNC_INTERVAL = 5;
    private final double[] relief = new double[PLUGS];
    private final float[] pressure = new float[PLUGS], offset = new float[PLUGS], sentPressure = new float[PLUGS];
    private int airborneMask;

    public record Step(int movingMask, int ventingMask, int landedMask, boolean stateChanged) { }

    /**
     * Advances all four independent plugs. Water temperatures, flows and insertion fractions are indexed NW, NE, SW, SE.
     */
    public Step tick(int blockX, int blockZ, long gameTime, int activeMask, double[] temperatures, double[] flows, double[] insertions) {
        if (temperatures.length < PLUGS || flows.length < PLUGS || insertions.length < PLUGS)
            throw new IllegalArgumentException("Four plug samples are required");
        int moving = 0, venting = 0, landed = 0;
        boolean stateChanged = false;
        for (int i = 0; i < PLUGS; i++) {
            double before = relief[i];
            relief[i] = Math.max(0, relief[i] - RELIEF_DECAY);
            double raw = localPressure((activeMask & 1 << i) != 0, temperatures[i], flows[i], insertions[i]);
            double effective = Math.max(0, raw - relief[i]);
            offset[i] = lift(blockX, blockZ, i, gameTime, effective);
            if (offset[i] > 0) {
                moving |= 1 << i;
                airborneMask |= 1 << i;
            } else if ((airborneMask & 1 << i) != 0) {
                landed |= 1 << i;
                airborneMask &= ~(1 << i);
            }
            if (offset[i] > RELIEF_OPEN_METRES) {
                venting |= 1 << i;
                relief[i] = Math.min(raw, relief[i] + RELIEF_BASE + RELIEF_LIFT * offset[i] / MAX_LIFT_METRES);
            }
            pressure[i] = (float) effective;
            stateChanged |= relief[i] != before;
        }
        return new Step(moving, venting, landed, stateChanged);
    }

    /** Pseudo-pressure from the channel water nearing boiling minus local flow cooling, normalised so one is the lift threshold, about 3 K below the channel boil. */
    public static double localPressure(boolean active, double waterKelvin, double flowMbPerTick, double rodInsertion) {
        if (!active || !Double.isFinite(waterKelvin) || !Double.isFinite(flowMbPerTick) || !Double.isFinite(rodInsertion)) return 0;
        double thermal = Math.clamp((waterKelvin - WARM_KELVIN) / WARM_RANGE, 0, MAX_HEAT);
        double heatGeneration = thermal * (1 - ROD_HEAT_REDUCTION * Math.clamp(rodInsertion, 0, 1));
        double cooling = Math.max(0, flowMbPerTick) / FLOW_REFERENCE;
        return Math.max(0, (heatGeneration - cooling) / PRESSURE_THRESHOLD);
    }

    /** Fourth-power impulse waveform; pressure below one returns exact zero. */
    public static float lift(int blockX, int blockZ, int slot, double timeTicks, double localPressure) {
        if (slot < 0 || slot >= PLUGS || !(localPressure >= 1) || !Double.isFinite(timeTicks)) return 0;
        double seed = unit(hash(blockX, blockZ, slot, 0, 0));
        double omega = 4.8 + 1.6 * seed;
        double phase = Math.TAU * unit(hash(blockX, blockZ, slot, 0, 1));
        double wave = Math.sin(omega * timeTicks * ReactorPhysics.TICK_SECONDS + phase) + noise(blockX, blockZ, slot, timeTicks);
        double impulse = Math.pow(Math.max(0, wave), 4);
        double pressureScale = Math.clamp(.7 + .45 * (localPressure - 1), .7, 1.35);
        return (float) Math.min(MAX_LIFT_METRES, impulse * LIFT_AMPLITUDE * pressureScale);
    }

    /** The renderer evaluates the same deterministic waveform between server pressure updates. */
    public float renderOffset(int blockX, int blockZ, int slot, double timeTicks) {
        return slot < 0 || slot >= PLUGS ? 0 : lift(blockX, blockZ, slot, timeTicks, pressure[slot]);
    }

    public float pressure(int slot) { return slot < 0 || slot >= PLUGS ? 0 : pressure[slot]; }
    public float serverOffset(int slot) { return slot < 0 || slot >= PLUGS ? 0 : offset[slot]; }
    public double relief(int slot) { return slot < 0 || slot >= PLUGS ? 0 : relief[slot]; }

    public boolean shouldSync(long gameTime, long positionSeed) {
        boolean crossed = false, changed = false;
        for (int i = 0; i < PLUGS; i++) {
            crossed |= pressure[i] >= 1 != sentPressure[i] >= 1;
            changed |= Math.abs(pressure[i] - sentPressure[i]) >= .04f;
        }
        return (crossed || changed) && Math.floorMod(gameTime + positionSeed, SYNC_INTERVAL) == 0;
    }

    public void markSynced() { System.arraycopy(pressure, 0, sentPressure, 0, PLUGS); }

    /** Ponder-only client preview; ordinary gameplay receives these values from the server. */
    public void preview(float... values) {
        for (int i = 0; i < PLUGS; i++) pressure[i] = i < values.length && Float.isFinite(values[i]) ? Math.max(0, values[i]) : 0;
    }

    public void write(CompoundTag tag, boolean clientPacket) {
        for (int i = 0; i < PLUGS; i++) {
            if (clientPacket) tag.putFloat("PlugPressure" + i, pressure[i]);
            else if (relief[i] > 0) tag.putDouble("PlugRelief" + i, relief[i]);
        }
    }

    public void read(CompoundTag tag, boolean clientPacket) {
        airborneMask = 0;
        for (int i = 0; i < PLUGS; i++) {
            offset[i] = 0;
            if (clientPacket) pressure[i] = Finite.of(tag.getFloat("PlugPressure" + i), 0, 0, Float.MAX_VALUE);
            else {
                relief[i] = Finite.of(tag.getDouble("PlugRelief" + i), 0, 0, Double.MAX_VALUE);
                pressure[i] = sentPressure[i] = 0;
            }
        }
    }

    private static double noise(int x, int z, int slot, double timeTicks) {
        double sample = timeTicks / 7d;
        long cell = (long) Math.floor(sample);
        double fraction = sample - cell;
        double smooth = fraction * fraction * (3 - 2 * fraction);
        double a = 2 * unit(hash(x, z, slot, cell, 2)) - 1;
        double b = 2 * unit(hash(x, z, slot, cell + 1, 2)) - 1;
        return (a + (b - a) * smooth) * NOISE_AMPLITUDE;
    }

    private static long hash(int x, int z, int slot, long time, int salt) {
        long value = 0x9e3779b97f4a7c15L;
        value ^= (long) x * 0x632be59bd9b4e019L;
        value ^= (long) z * 0x9e3779b185ebca87L;
        value ^= (long) slot * 0xc2b2ae3d27d4eb4fL;
        value ^= time * 0x165667b19e3779f9L;
        value ^= (long) salt * 0x85ebca77c2b2ae63L;
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    private static double unit(long bits) { return (bits >>> 11) * 0x1.0p-53; }
}
