package net.createplant.item;

import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.TankGrade;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/** The stainless steel bucket holds what a stainless tank is rated for; a bucket filled from it is marked and empties back into it. */
public final class StainlessBuckets {
    private StainlessBuckets() { }

    public static boolean holds(FluidStack fluid) { return !fluid.isEmpty() && TankGrade.STAINLESS.accepts(fluid); }

    public static boolean marked(ItemStack stack) { return stack.has(CEWorldFluids.STAINLESS_SHELL.get()); }

    public static ItemStack empty() { return new ItemStack(CEWorldFluids.STAINLESS_BUCKET.get()); }

    public static ItemStack mark(ItemStack filled) {
        if (!filled.isEmpty()) filled.set(CEWorldFluids.STAINLESS_SHELL.get(), Unit.INSTANCE);
        return filled;
    }

    /** The marked bucket of a liquid the stainless bucket holds; empty when it does not hold it or the liquid has no bucket. */
    public static ItemStack filled(FluidStack fluid) {
        if (!holds(fluid)) return ItemStack.EMPTY;
        var bucket = CEWorldFluids.bucket(fluid);
        return mark(bucket.isEmpty() ? StatefulBuckets.write(fluid, FluidUtil.getFilledBucket(fluid)) : bucket);
    }

    /** What an emptied bucket leaves: the stainless bucket for a marked one, otherwise its usual empty bucket. */
    public static ItemStack emptied(ItemStack filled, ItemStack usual) { return marked(filled) ? empty() : usual; }
}
