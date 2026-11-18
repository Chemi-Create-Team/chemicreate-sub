package net.createplant.thermal;

import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Optional outdoor heat-transfer rates; no changes to material or electrical energy ledgers. */
public final class SeasonalThermal {
    public record Factors(double heating, double cooling) { }
    private record Sample(long tick, Factors factors) { }
    public static final Factors NEUTRAL = new Factors(1, 1);
    private static final boolean PRESENT = net.neoforged.fml.ModList.get().isLoaded("sereneseasons");
    private static final WeakHashMap<Level, Sample> CACHE = new WeakHashMap<>();
    private SeasonalThermal() { }
    public static boolean available() { return PRESENT; }
    private static Factors sample(Level level) {
        if (!PRESENT || level == null) return NEUTRAL;
        synchronized (CACHE) {
            long now = level.getGameTime();var old = CACHE.get(level);
            if (old != null && now >= old.tick && now - old.tick < 20) return old.factors;
            var value = net.createplant.compat.seasons.SereneSeasonsThermal.sample(level);CACHE.put(level, new Sample(now, value));return value;
        }
    }
    public static double heating(Level level) { return sample(level).heating; }
    public static double cooling(Level level) { return sample(level).cooling; }
    public static Component reading(Level level, boolean heating) { return Component.translatable("seasonal.create_refinery_n_reactors." + (heating ? "heating" : "cooling"), Math.round(100 * (heating ? heating(level) : cooling(level)))); }
}
