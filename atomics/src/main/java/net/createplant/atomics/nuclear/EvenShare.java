package net.createplant.atomics.nuclear;

/** Splits an amount over channels as evenly as their room allows; a channel that fills up passes its surplus to the others. */
public final class EvenShare {
    private static final int PASSES = 16;

    private EvenShare() { }

    /** How much of {@code amount} each channel gets; the remainders of the division start at {@code start} and move on with it. */
    public static int[] split(int amount, int[] room, int start) {
        int n = room.length;
        int[] share = new int[n], left = room.clone();
        int remaining = amount;
        for (int pass = 0; pass < PASSES && remaining > 0; pass++) {
            int open = 0;
            for (int r : left) if (r > 0) open++;
            if (open == 0) break;
            int each = remaining / open, extra = remaining % open, seen = 0, given = 0;
            for (int k = 0; k < n; k++) {
                int i = (start + k) % n;
                if (left[i] <= 0) continue;
                int give = Math.min(left[i], each + (seen++ < extra ? 1 : 0));
                share[i] += give;
                left[i] -= give;
                given += give;
            }
            remaining -= given;
            if (given == 0) break;
        }
        for (int k = 0; k < n && remaining > 0; k++) {
            int i = (start + k) % n, give = Math.min(left[i], remaining);
            share[i] += give;
            remaining -= give;
        }
        return share;
    }
}
