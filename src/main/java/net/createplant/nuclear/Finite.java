package net.createplant.nuclear;

import java.util.Arrays;

/**
 * Saved and synchronised values of the reactor plant made safe to use. NaN and infinity pass Math.max and Math.clamp unchanged, so a value
 * that is not a finite number comes back as the given fallback, and a finite one is held in its range.
 */
public final class Finite {
    private Finite() { }

    public static double of(double value, double fallback, double low, double high) { return Double.isFinite(value) ? Math.clamp(value, low, high) : fallback; }

    public static float of(float value, float fallback, float low, float high) { return Float.isFinite(value) ? Math.clamp(value, low, high) : fallback; }

    /** Every entry in range, and exactly the given length: a short or long array from a bad packet is padded or cut. */
    public static float[] all(float[] values, int length, float fallback, float low, float high) {
        float[] result = Arrays.copyOf(values, length);
        for (int i = 0; i < length; i++) result[i] = i < values.length ? of(result[i], fallback, low, high) : fallback;
        return result;
    }
}
