package net.createplant.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;
import com.google.common.collect.Iterables;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The quarry's way out for what it digs: the bin on the drill head, the bellows chute straight up to the bridge, and the belt
 * along the bridge to the driven rail, where each parcel is set onto the real Create belt beside that rail. Parcels keep their
 * order and wait, never vanish, while the stage ahead is full.
 */
public final class QuarryConveyor {
    // Keep persisted parcel timestamps independent of the quarry work rate.
    public static final int SAVED_CLOCK_SCALE = 4;
    public static final int HEAD_SLOTS = 9, PARCEL = 16, MAX_RISING = 4, LAUNCH_GAP = 10, BRIDGE_GAP = 8;
    public static final int MAX_VISIBLE_PARCELS = 40;
    /** Travel speed in the chute and on the bridge belt, in blocks per tick. */
    public static final double SPEED = .25, CHUTE_OFFSET = .56, BELT_HEIGHT = 12 / 16d, ITEM_CLEARANCE = .12;

    /** A parcel on the move: rising in the chute, or on the bridge belt, from game time {@code start} for {@code duration} ticks. */
    public record Parcel(ItemStack stack, boolean bridge, long start, int duration) {
        public double progress(long now, float partialTicks) { return Math.clamp((now - start + partialTicks) / Math.max(1, duration), 0, 1); }
        boolean arrived(long now) { return now - start >= duration; }
    }

    private final Runnable changed;
    private final ItemStackHandler head = new ItemStackHandler(HEAD_SLOTS) {
        @Override protected void onContentsChanged(int slot) { changed.run(); }
    };
    private final Deque<Parcel> bridge = new ArrayDeque<>(), rising = new ArrayDeque<>();
    private final Iterable<Parcel> parcels = Iterables.concat(bridge, rising);
    private int syncedParcelCount = -1;
    /** Far enough back that the first parcel leaves at once, without the overflow of {@code Long.MIN_VALUE}. */
    private static final long NEVER = -1_000_000;
    private long lastLaunch = NEVER;

    /** {@code changed} saves the drive. */
    public QuarryConveyor(Runnable changed) { this.changed = changed; }

    public IItemHandler head() { return head; }
    public Iterable<Parcel> parcels() { return parcels; }
    public int parcelCount() { return syncedParcelCount < 0 ? bridge.size() + rising.size() : syncedParcelCount; }

    boolean hasItems() {
        if (!bridge.isEmpty() || !rising.isEmpty()) return true;
        for (int slot = 0; slot < HEAD_SLOTS; slot++) if (!head.getStackInSlot(slot).isEmpty()) return true;
        return false;
    }

    /** Whether all of {@code drops} fit in the head bin. */
    public boolean fits(List<ItemStack> drops) {
        var trial = new ItemStackHandler(HEAD_SLOTS);
        for (int slot = 0; slot < HEAD_SLOTS; slot++) trial.setStackInSlot(slot, head.getStackInSlot(slot).copy());
        for (ItemStack drop : drops) if (!ItemHandlerHelper.insertItem(trial, drop.copy(), false).isEmpty()) return false;
        return true;
    }

    public void collect(List<ItemStack> drops) { for (ItemStack drop : drops) ItemHandlerHelper.insertItem(head, drop.copy(), false); }

    /**
     * One server tick. The bridge parcel at the rail end goes onto the side belt through {@code unload}, which returns what the
     * belt left; a risen parcel steps onto the bridge; and the head sends a new parcel up. {@code rise} and {@code cross} are the
     * chute and bridge lengths now. Returns whether anything moved, for synchronisation.
     */
    public boolean tick(long now, double rise, double cross, UnaryOperator<ItemStack> unload) {
        boolean moved = false;
        Parcel first = bridge.peekFirst();
        if (first != null && first.arrived(now)) {
            ItemStack left = unload.apply(first.stack().copy());
            if (left.getCount() != first.stack().getCount()) {
                bridge.removeFirst();
                if (!left.isEmpty()) bridge.addFirst(new Parcel(left, true, first.start(), first.duration()));
                moved = true;
            }
        }
        int crossingTicks = duration(cross);
        Parcel last = bridge.peekLast(), next = rising.peekFirst();
        if (next != null && next.arrived(now) && bridge.size() < Math.ceilDiv(crossingTicks, BRIDGE_GAP)
            && (last == null || now - last.start() >= BRIDGE_GAP)) {
            rising.removeFirst();
            bridge.addLast(new Parcel(next.stack(), true, now, crossingTicks));
            moved = true;
        }
        if (rising.size() < MAX_RISING && now - lastLaunch >= LAUNCH_GAP) for (int slot = 0; slot < HEAD_SLOTS; slot++) {
            ItemStack sent = head.extractItem(slot, PARCEL, false);
            if (sent.isEmpty()) continue;
            rising.addLast(new Parcel(sent, false, now, duration(rise)));
            lastLaunch = now;
            moved = true;
            break;
        }
        return moved;
    }

    private static int duration(double length) { return (int) Math.max(4, Math.ceil(length / SPEED)); }

    /** Everything held, for a drive that is broken. */
    public List<ItemStack> contents() {
        var all = new ArrayList<ItemStack>();
        for (int slot = 0; slot < HEAD_SLOTS; slot++) all.add(head.getStackInSlot(slot));
        for (Parcel parcel : parcels) all.add(parcel.stack());
        all.removeIf(ItemStack::isEmpty);
        return all;
    }

    public void clear() {
        for (int slot = 0; slot < HEAD_SLOTS; slot++) head.setStackInSlot(slot, ItemStack.EMPTY);
        bridge.clear();
        rising.clear();
        syncedParcelCount = -1;
        lastLaunch = NEVER;
    }

    public void write(CompoundTag tag, HolderLookup.Provider lookup, boolean packet, long now) {
        tag.putLong("Now", now / SAVED_CLOCK_SCALE);
        var list = new ListTag();
        if (packet) {
            tag.putInt("ParcelCount", parcelCount());
            writeVisibleParcels(list, lookup, now);
        } else {
            tag.put("Head", head.serializeNBT(lookup));
            tag.putLong("LastLaunch", lastLaunch);
            for (Parcel parcel : parcels) list.add(writeParcel(parcel, lookup));
        }
        tag.put("Parcels", list);
    }

    private void writeVisibleParcels(ListTag list, HolderLookup.Provider lookup, long now) {
        if (!bridge.isEmpty()) {
            // Keep the lead parcel at the outlet and look for moving parcels near the inlet.
            list.add(writeParcel(bridge.getFirst(), lookup));
            var newest = bridge.descendingIterator();
            int count = Math.min(bridge.size() - 1, MAX_VISIBLE_PARCELS - MAX_RISING - 1);
            for (int i = 0; i < count; i++) {
                Parcel parcel = newest.next();
                if (!parcel.arrived(now)) list.add(writeParcel(parcel, lookup));
            }
        }
        int count = 0;
        for (Parcel parcel : rising) {
            if (count++ == MAX_RISING) break;
            list.add(writeParcel(parcel, lookup));
        }
    }

    private static CompoundTag writeParcel(Parcel parcel, HolderLookup.Provider lookup) {
        var entry = new CompoundTag();
        entry.put("Stack", parcel.stack().save(lookup));
        entry.putBoolean("Bridge", parcel.bridge());
        entry.putLong("Start", parcel.start());
        entry.putInt("Duration", parcel.duration());
        return entry;
    }

    public long read(CompoundTag tag, HolderLookup.Provider lookup, boolean packet) {
        var list = tag.getList("Parcels", Tag.TAG_COMPOUND);
        long now = Math.max(0, tag.getLong("Now")) * SAVED_CLOCK_SCALE;
        bridge.clear();
        rising.clear();
        if (!packet) {
            head.deserializeNBT(lookup, tag.getCompound("Head"));
            lastLaunch = tag.contains("LastLaunch") ? tag.getLong("LastLaunch") : NEVER;
            now = Math.max(now, lastLaunch);
        }
        syncedParcelCount = packet ? Math.max(list.size(), tag.getInt("ParcelCount")) : -1;
        int skipped = packet ? Math.max(0, list.size() - MAX_VISIBLE_PARCELS) : 0;
        for (int i = 0; i < list.size() - skipped; i++) {
            var entry = list.getCompound(i == 0 ? 0 : i + skipped);
            ItemStack stack = ItemStack.parseOptional(lookup, entry.getCompound("Stack"));
            if (stack.isEmpty()) continue;
            var parcel = new Parcel(stack, entry.getBoolean("Bridge"), entry.getLong("Start"), Math.max(1, entry.getInt("Duration")));
            (parcel.bridge() ? bridge : rising).addLast(parcel);
            if (!packet) now = Math.max(now, parcel.start());
        }
        // Older saves can have parcel starts ahead of the saved drive clock.
        return Math.ceilDiv(now, SAVED_CLOCK_SCALE) * SAVED_CLOCK_SCALE;
    }
}
