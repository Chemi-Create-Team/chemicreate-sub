package net.createplant.nuclear;

import java.util.List;

/** Game-independent dimensions and fine-channel counts for the supported RBMK core variants. */
public final class ReactorCoreVariants {
    public static final int MIN_WIDTH = 5, MAX_WIDTH = 13, SHIELD_THICKNESS = 2;
    public static final List<Integer> RECOMMENDED_DIAMETERS = List.of(5, 7, 9, 11, 13);

    public record Variant(int coreDiameter, int outerDiameter, int bundleColumns, int fineChannels,
                          int fuelChannels, int controlChannels) { }

    private ReactorCoreVariants() { }

    public static int outerDiameter(int coreDiameter) { return coreDiameter + 2 * SHIELD_THICKNESS; }

    public static boolean inCircle(int i, int j, int size) {
        double r = size / 2d, x = i + 0.5 - r, z = j + 0.5 - r;
        return x * x + z * z <= r * r;
    }

    public static Variant circular(int size) {
        if (size < MIN_WIDTH || size > MAX_WIDTH) throw new IllegalArgumentException("Core diameter " + size + " is outside " + MIN_WIDTH + " to " + MAX_WIDTH);
        int bundles = 0, active = 0, controls = 0;
        for (int bx = 0; bx < size; bx++) for (int bz = 0; bz < size; bz++) {
            if (!inCircle(bx, bz, size)) continue;
            bundles++;
            for (int slot = 0; slot < ReactorPhysics.RODS_PER_BLOCK; slot++) {
                int sx = slot % ReactorPhysics.CHANNEL_GRID, sz = slot / ReactorPhysics.CHANNEL_GRID;
                if (!activeChannel(bx, bz, sx, sz, size)) continue;
                active++;
                if (defaultControlChannel(bx, bz, slot, size, true)) controls++;
            }
        }
        return new Variant(size, outerDiameter(size), bundles, active, active - controls, controls);
    }

    static boolean activeChannel(int bx, int bz, int sx, int sz, int size) {
        int fine = size * ReactorPhysics.CHANNEL_GRID;
        int x = 2 * (bx * ReactorPhysics.CHANNEL_GRID + sx) + 1 - fine;
        int z = 2 * (bz * ReactorPhysics.CHANNEL_GRID + sz) + 1 - fine;
        int square = x * x + z * z;
        // Keep the same clipped circular edge in block space when the number of fine channels per block changes.
        double diameter = fine - 1.05 * ReactorPhysics.CHANNEL_GRID;
        return square <= diameter * diameter;
    }

    static boolean defaultControlChannel(int bx, int bz, int slot, int size, boolean circular) {
        int fine = size * ReactorPhysics.CHANNEL_GRID, c0 = fine / 2 - 1, c1 = fine / 2;
        int x = bx * ReactorPhysics.CHANNEL_GRID + slot % ReactorPhysics.CHANNEL_GRID;
        int z = bz * ReactorPhysics.CHANNEL_GRID + slot / ReactorPhysics.CHANNEL_GRID;
        if (circular && size == MIN_WIDTH) {
            // Reflection across either axis maps every slot to another rod.
            return switch (Math.min(z, fine - 1 - z)) {
                case 2 -> x == 3 || x == 6;
                case 3 -> x == 2 || x == 7;
                default -> false;
            };
        }
        return x % 3 == 1 && z % 3 == 1 || x == c1 && z == c0 || x == c0 && z == c1;
    }

    static boolean previousMinimumControlChannel(int bx, int bz, int slot) {
        int x = bx * ReactorPhysics.CHANNEL_GRID + slot % ReactorPhysics.CHANNEL_GRID;
        int z = bz * ReactorPhysics.CHANNEL_GRID + slot / ReactorPhysics.CHANNEL_GRID;
        return switch (z) {
            case 2 -> x == 4 || x == 6;
            case 3, 5 -> x == 2;
            case 4, 6 -> x == 7;
            case 7 -> x == 3 || x == 5;
            default -> false;
        };
    }
}
