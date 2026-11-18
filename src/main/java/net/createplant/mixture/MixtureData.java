package net.createplant.mixture;

import com.mojang.serialization.Codec;
import java.util.*;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Per-fluid properties stay separate from the canonical integer volume ledger. */
public record MixtureData(Map<ResourceLocation, DataComponentPatch> components) {
    public static final MixtureData EMPTY = new MixtureData(Map.of());
    public static final Codec<MixtureData> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, DataComponentPatch.CODEC).xmap(MixtureData::new, MixtureData::components);
    public static final StreamCodec<RegistryFriendlyByteBuf, MixtureData> STREAM_CODEC = ByteBufCodecs
        .<RegistryFriendlyByteBuf, ResourceLocation, DataComponentPatch, Map<ResourceLocation, DataComponentPatch>>map(size -> new TreeMap<>(), ResourceLocation.STREAM_CODEC, DataComponentPatch.STREAM_CODEC)
        .map(MixtureData::new, MixtureData::components);
    public MixtureData {
        var sorted = new TreeMap<ResourceLocation, DataComponentPatch>();
        components.forEach((id, patch) -> { if (!patch.isEmpty()) sorted.put(Objects.requireNonNull(id), Objects.requireNonNull(patch)); });
        components = Collections.unmodifiableMap(sorted);
    }
    public MixtureData forContents(MixtureContents contents) {
        var result = new TreeMap<ResourceLocation, DataComponentPatch>();
        components.forEach((id, patch) -> { if (contents.amounts().containsKey(id)) result.put(id, patch); });return new MixtureData(result);
    }
}
