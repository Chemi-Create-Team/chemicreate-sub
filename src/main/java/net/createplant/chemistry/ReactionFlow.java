package net.createplant.chemistry;

/**
 * A reaction converts a little every tick instead of all of a batch at the end of its cycle. {@code flow} is how far through the batch it is (0 to 1)
 * and {@code f} how many batches it moves on this tick. The feed goes out ahead of the products (rounded up, products rounded down), so nothing is
 * made from nothing, and over a whole batch exactly the recipe's amounts go in and come out.
 */
public final class ReactionFlow {
    private ReactionFlow() { }

    /** What the reaction takes from an amount per batch while it moves on by {@code f}. */
    public static int owed(int amount, double flow, double f) { return (int) (Math.ceil(amount * (flow + f) - 1e-9) - Math.ceil(amount * flow - 1e-9)); }

    /** What the reaction has made of an amount per batch while it moves on by {@code f}. */
    public static int made(int amount, double flow, double f) { return (int) (Math.floor(amount * (flow + f) + 1e-9) - Math.floor(amount * flow + 1e-9)); }

    /** The position in the batch after moving on by {@code f}. */
    public static double next(double flow, double f) { double sum = flow + f, rest = sum - Math.floor(sum); return rest > 1 - 1e-9 ? 0 : rest; }
}
