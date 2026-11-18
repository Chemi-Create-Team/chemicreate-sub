package net.createplant.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.createplant.registry.CEWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Places an ore through a height range at a fixed number of tries per 16 blocks, so the tries grow with the range. With the
 * lower end tied to the world bottom and the upper end at the vanilla floor, a world made deeper by another mod (for example
 * Tectonic's Y -192) gets the ore in the added depth, while the range is empty in a vanilla-depth world.
 */
public final class DeepLayerPlacement extends PlacementModifier {
    public static final MapCodec<DeepLayerPlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(placement -> placement.minInclusive),
            VerticalAnchor.CODEC.fieldOf("max_inclusive").forGetter(placement -> placement.maxInclusive),
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("per_16_blocks").forGetter(placement -> placement.per16Blocks))
        .apply(instance, DeepLayerPlacement::new));

    private final VerticalAnchor minInclusive;
    private final VerticalAnchor maxInclusive;
    private final float per16Blocks;

    private DeepLayerPlacement(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, float per16Blocks) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.per16Blocks = per16Blocks;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int bottom = minInclusive.resolveY(context);
        int top = maxInclusive.resolveY(context);
        if (top < bottom)
            return Stream.empty();
        float tries = per16Blocks * (top - bottom + 1) / 16;
        int count = Mth.floor(tries) + (random.nextFloat() < Mth.frac(tries) ? 1 : 0);
        return IntStream.range(0, count).mapToObj(i -> pos.atY(Mth.randomBetweenInclusive(random, bottom, top)));
    }

    @Override
    public PlacementModifierType<?> type() {
        return CEWorldgen.DEEP_LAYER.get();
    }
}
