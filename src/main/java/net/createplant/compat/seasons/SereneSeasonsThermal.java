package net.createplant.compat.seasons;

import net.createplant.thermal.SeasonalThermal;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.init.ModConfig;

/** Loaded only when Serene Seasons is present. */
public final class SereneSeasonsThermal {
    private SereneSeasonsThermal() { }
    public static SeasonalThermal.Factors factors(Season season) {
        return switch (season) {
            case SPRING -> SeasonalThermal.NEUTRAL;
            case SUMMER -> new SeasonalThermal.Factors(1.2, .8);
            case AUTUMN -> new SeasonalThermal.Factors(.95, 1.05);
            case WINTER -> new SeasonalThermal.Factors(.8, 1.2);
        };
    }
    public static SeasonalThermal.Factors sample(Level level) {
        if (SeasonHelper.dataProvider == null || ModConfig.seasons == null || !ModConfig.seasons.isDimensionWhitelisted(level.dimension())) return SeasonalThermal.NEUTRAL;
        return factors(SeasonHelper.getSeasonState(level).getSeason());
    }
}
