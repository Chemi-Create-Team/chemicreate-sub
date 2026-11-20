package net.createplant.agriculture;

import java.util.Locale;
import net.minecraft.util.RandomSource;

/** Game balance for crop blocks. Durations are measured in server ticks. */
public enum FertilizerProfile {
    SULFATE(1, 1, 2, 24_000, 25, 1, 1),
    NITRATE(1, 1, 3, 24_000, 45, 1, 1),
    UREA(1, 1, 4, 36_000, 55, 1, 1),
    PHOSPHATE(1, 1, 2, 36_000, 65, 1, 2),
    NPK(1, 2, 4, 48_000, 100, 2, 3);

    public final int radius, bonusStages, growthUses, duration, harvestChance, minimumHarvest, maximumHarvest;

    FertilizerProfile(int radius, int bonusStages, int growthUses, int duration, int harvestChance, int minimumHarvest, int maximumHarvest) {
        this.radius = radius;
        this.bonusStages = bonusStages;
        this.growthUses = growthUses;
        this.duration = duration;
        this.harvestChance = harvestChance;
        this.minimumHarvest = minimumHarvest;
        this.maximumHarvest = maximumHarvest;
    }

    public int rollHarvest(RandomSource random) {
        return random.nextInt(100) < harvestChance ? minimumHarvest + random.nextInt(maximumHarvest - minimumHarvest + 1) : 0;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    static FertilizerProfile byId(String id) {
        for (var profile : values()) if (profile.id().equals(id)) return profile;
        return null;
    }
}
