package net.createplant.block;

/** Integer-grid footprint for the tank's symmetric pixel-circle pattern. */
public final class TankFootprint {
    public static final int MIN_DIAMETER = 4;
    public static final int MAX_DIAMETER = 32;
    private static final int[] BLOCKS = calculateBlocks();

    private TankFootprint() { }

    public static boolean contains(int x, int z, int diameter) {
        if (x < 0 || z < 0 || x >= diameter || z >= diameter)
            return false;
        int dx = 2 * x - (diameter - 1);
        int dz = 2 * z - (diameter - 1);
        return dx * dx + dz * dz <= diameter * diameter;
    }

    public static boolean isWall(int x, int z, int diameter) {
        return contains(x, z, diameter)
            && (!contains(x + 1, z, diameter) || !contains(x - 1, z, diameter)
                || !contains(x, z + 1, diameter) || !contains(x, z - 1, diameter));
    }

    /** Number of cells in one cross-section of the circle, wall ring included. */
    public static int blocks(int diameter) {
        return diameter >= MIN_DIAMETER && diameter <= MAX_DIAMETER ? BLOCKS[diameter] : 0;
    }

    private static int[] calculateBlocks() {
        int[] counts = new int[MAX_DIAMETER + 1];
        for (int diameter = MIN_DIAMETER; diameter <= MAX_DIAMETER; diameter++) {
            for (int x = 0; x < diameter; x++) {
                for (int z = 0; z < diameter; z++) {
                    if (contains(x, z, diameter))
                        counts[diameter]++;
                }
            }
        }
        return counts;
    }
}
