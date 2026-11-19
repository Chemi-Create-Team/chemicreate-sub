package net.createplant.cloud;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GasCloudStyles {
    public record Style(boolean ceiling, Integer color, int outdoorDivisor) {
        public Style {
            if (outdoorDivisor < 1 || outdoorDivisor > 2400) throw new IllegalArgumentException("Invalid outdoor divisor");
            if (color != null && (color < 0 || color > 0xFFFFFF)) throw new IllegalArgumentException("Expected a 24-bit RGB color");
        }
    }

    private static final class Styles {
        private static final Map<String, Style> BY_ID = defaults();

        private static Map<String, Style> defaults() {
            var result = new ConcurrentHashMap<String, Style>();
            var steam = new Style(true, 0xF5F8FF, 6);
            for (var grade : SteamGrades.values()) result.put(id(grade.fluid()), steam);
            return result;
        }
    }

    private GasCloudStyles() { }

    public static void register(Fluid fluid, Style style) {
        if (SteamGrades.of(new FluidStack(fluid, 1)) == null) throw new IllegalArgumentException("Gas cloud styles are limited to steam");
        Styles.BY_ID.put(id(fluid), style);
    }

    static String id(Fluid fluid) { return BuiltInRegistries.FLUID.getKey(fluid).toString(); }
    static Style find(String fluidId) { return Styles.BY_ID.get(fluidId); }
}
