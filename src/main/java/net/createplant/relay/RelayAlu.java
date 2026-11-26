package net.createplant.relay;

/**
 * A relay adder/subtractor simulated relay by relay. Each relay's contacts follow its coil one step later, so carries
 * ripple through the bank like in a real relay machine. Every arithmetic and comparison the relay computer makes goes
 * through {@link #run}; the relays keep their positions between operations, so only the relays that change click.
 * Per bit: input relays A and B, carry relay C and sum relay S, plus one SUB relay that inverts B (through its break
 * contacts) and feeds the first carry for two's complement subtraction.
 */
public final class RelayAlu {
    public static final int BITS = 12, MASK = (1 << BITS) - 1;
    /** Relays: A, B, S per bit, BITS + 1 carries and SUB. */
    public static final int RELAYS = BITS * 3 + BITS + 1 + 1;
    private final boolean[] a = new boolean[BITS], b = new boolean[BITS], s = new boolean[BITS], c = new boolean[BITS + 1];
    private boolean sub;
    private long flips, steps;

    /** Relay switchings and relay steps (one contact transfer time each) since the counters were last taken. */
    public long takeFlips() { long f = flips; flips = 0; return f; }
    public long takeSteps() { long t = steps; steps = 0; return t; }

    /** x + y, or x - y when subtract; signed BITS-bit two's complement. */
    public int run(int x, int y, boolean subtract) {
        boolean[] na = new boolean[BITS], nb = new boolean[BITS], ns = new boolean[BITS], nc = new boolean[BITS + 1];
        for (int guard = 0; guard < 4 * BITS; guard++) {
            boolean nsub = subtract;
            nc[0] = sub;
            for (int i = 0; i < BITS; i++) {
                na[i] = (x >> i & 1) != 0;
                nb[i] = (y >> i & 1) != 0;
                boolean bi = b[i] ^ sub; // B through the SUB relay's make or break contact
                nc[i + 1] = a[i] && bi || a[i] && c[i] || bi && c[i];
                ns[i] = a[i] ^ bi ^ c[i];
            }
            int changed = count(a, na) + count(b, nb) + count(s, ns) + count(c, nc) + (nsub != sub ? 1 : 0);
            if (changed == 0) break;
            flips += changed;
            steps++;
            System.arraycopy(na, 0, a, 0, BITS); System.arraycopy(nb, 0, b, 0, BITS);
            System.arraycopy(ns, 0, s, 0, BITS); System.arraycopy(nc, 0, c, 0, BITS + 1);
            sub = nsub;
        }
        int r = 0;
        for (int i = 0; i < BITS; i++) if (s[i]) r |= 1 << i;
        return r << (32 - BITS) >> (32 - BITS);
    }

    public int add(int x, int y) { return run(x, y, false); }
    public int sub(int x, int y) { return run(x, y, true); }
    /** x >= y: the sign relay of x - y stays released. */
    public boolean ge(int x, int y) { return sub(x, y) >= 0; }
    public int min(int x, int y) { return ge(x, y) ? y : x; }
    public int max(int x, int y) { return ge(x, y) ? x : y; }
    /** A right shift is only wiring: bit i is read from relay i + n. */
    public static int shift(int x, int n) { return x >> n; }

    private static int count(boolean[] from, boolean[] to) {
        int n = 0;
        for (int i = 0; i < from.length; i++) if (from[i] != to[i]) n++;
        return n;
    }

    /** Clamps a sensor reading into the bank's range (the inputs are meter contacts, not relays). */
    public static int input(double v) { return (int) Math.max(-(1 << BITS - 1), Math.min((1 << BITS - 1) - 1, Math.round(v))); }
}
