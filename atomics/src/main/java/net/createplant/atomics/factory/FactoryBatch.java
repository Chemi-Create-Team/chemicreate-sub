package net.createplant.atomics.factory;

import net.createplant.atomics.recipe.ContinuousFactoryRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Paid material and a snapshot of the recipe remain owned by the controller until output is accepted. */
public final class FactoryBatch {
    public final int slot, length, work, heat;
    public final float thickness, tolerance;
    public final ItemStack feed, auxiliary, result;
    public double travel, dose, slack;
    public ItemStack finished = ItemStack.EMPTY;
    public boolean damaged;

    public FactoryBatch(int slot, int length, ContinuousFactoryRecipe recipe, ItemStack feed, ItemStack auxiliary) {
        this(slot, length, recipe.work(), recipe.heat(), recipe.startingThickness(), recipe.rpmTolerance(), feed, auxiliary, recipe.result().copy());
    }

    public static FactoryBatch preview(int length) { return new FactoryBatch(0, length, 400, 0, .68f, .10f, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY); }

    private FactoryBatch(int slot, int length, int work, int heat, float thickness, float tolerance, ItemStack feed, ItemStack auxiliary, ItemStack result) {
        this.slot = slot;
        this.length = length;
        this.work = work;
        this.heat = heat;
        this.thickness = thickness;
        this.tolerance = tolerance;
        this.feed = feed;
        this.auxiliary = auxiliary;
        this.result = result;
    }

    public CompoundTag save(HolderLookup.Provider lookup) {
        var tag = new CompoundTag();
        tag.putInt("Slot", slot);
        tag.putInt("Length", length);
        tag.putInt("Work", work);
        tag.putInt("Heat", heat);
        tag.putFloat("Thickness", thickness);
        tag.putFloat("Tolerance", tolerance);
        tag.put("Feed", feed.saveOptional(lookup));
        tag.put("Auxiliary", auxiliary.saveOptional(lookup));
        tag.put("Result", result.saveOptional(lookup));
        tag.put("Finished", finished.saveOptional(lookup));
        tag.putDouble("Travel", travel);
        tag.putDouble("Dose", dose);
        tag.putDouble("Slack", slack);
        tag.putBoolean("Damaged", damaged);
        return tag;
    }

    public static FactoryBatch load(HolderLookup.Provider lookup, CompoundTag tag) {
        var batch = new FactoryBatch(Math.clamp(tag.getInt("Slot"), 0, 7), Math.clamp(tag.getInt("Length"), 6, FactoryLayout.MAX_LENGTH),
            Math.clamp(tag.getInt("Work"), 20, 12000), Math.clamp(tag.getInt("Heat"), 0, 2), Math.clamp(tag.getFloat("Thickness"), .06f, .9f),
            tag.contains("Tolerance") ? Math.clamp(tag.getFloat("Tolerance"), .01f, .5f) : .10f,
            ItemStack.parseOptional(lookup, tag.getCompound("Feed")), ItemStack.parseOptional(lookup, tag.getCompound("Auxiliary")),
            ItemStack.parseOptional(lookup, tag.getCompound("Result")));
        batch.finished = ItemStack.parseOptional(lookup, tag.getCompound("Finished"));
        batch.travel = Math.clamp(tag.getDouble("Travel"), 0, FactoryLayout.MAX_LENGTH);
        batch.dose = Math.clamp(tag.getDouble("Dose"), 0, 20000);
        batch.slack = Math.clamp(tag.getDouble("Slack"), 0, 2);
        batch.damaged = tag.getBoolean("Damaged");
        return batch;
    }
}
