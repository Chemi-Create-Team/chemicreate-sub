package net.createplant.mixture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Absolute component volumes; sorting also defines deterministic largest-remainder ties. */
public record MixtureContents(Map<ResourceLocation, Integer> amounts) {
    public static final MixtureContents EMPTY = new MixtureContents(Map.of());
    public static final Codec<MixtureContents> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).flatXmap(map -> {
        try { return DataResult.success(new MixtureContents(map)); }
        catch (IllegalArgumentException | ArithmeticException error) { return DataResult.error(error::getMessage); }
    }, contents -> DataResult.success(contents.amounts));
    public static final StreamCodec<RegistryFriendlyByteBuf, MixtureContents> STREAM_CODEC = ByteBufCodecs
        .<RegistryFriendlyByteBuf, ResourceLocation, Integer, Map<ResourceLocation, Integer>>map(size -> new TreeMap<>(), ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT)
        .map(MixtureContents::new, MixtureContents::amounts);

    public MixtureContents {
        var normalized = new TreeMap<ResourceLocation, Integer>();int total = 0;
        for (var entry : amounts.entrySet()) {
            Objects.requireNonNull(entry.getKey());Objects.requireNonNull(entry.getValue());
            if (entry.getValue() <= 0) continue;
            total = Math.addExact(total, entry.getValue());normalized.put(entry.getKey(), entry.getValue());
        }
        amounts = Collections.unmodifiableMap(normalized);
    }
    public int totalAmount() { int total = 0;for (int amount : amounts.values()) total += amount;return total; }
    public MixtureContents merge(MixtureContents other) {
        var combined = new TreeMap<>(amounts);
        other.amounts.forEach((id, amount) -> combined.merge(id, amount, Math::addExact));
        return new MixtureContents(combined);
    }
    public record SplitResult(MixtureContents extracted, MixtureContents remaining) { }
    private record Remainder(ResourceLocation id, long value) { }
    public SplitResult split(int extractAmount) {
        if (extractAmount < 0 || extractAmount > totalAmount()) throw new IllegalArgumentException("Extraction outside mixture volume");
        var extracted = resized(extractAmount);var remaining = new TreeMap<>(amounts);
        extracted.amounts.forEach((id, amount) -> remaining.compute(id, (key, old) -> old - amount));
        return new SplitResult(extracted, new MixtureContents(remaining));
    }
    /** Used for stack previews and explicit copies; actual removal uses the complementary split above. */
    public MixtureContents resized(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative mixture volume");
        if (amount == 0) return EMPTY;
        int total = totalAmount();if (total == 0) throw new IllegalArgumentException("Cannot enlarge an empty mixture");
        if (amount == total) return this;
        var result = new TreeMap<ResourceLocation, Integer>();var remainders = new ArrayList<Remainder>();int assigned = 0;
        for (var entry : amounts.entrySet()) {
            long product = (long) entry.getValue() * amount;int floor = (int) (product / total);
            result.put(entry.getKey(), floor);assigned += floor;remainders.add(new Remainder(entry.getKey(), product % total));
        }
        remainders.sort(Comparator.comparingLong(Remainder::value).reversed().thenComparing(Remainder::id));
        for (int i = 0; i < amount - assigned; i++) result.merge(remainders.get(i).id, 1, Integer::sum);
        return new MixtureContents(result);
    }
}
