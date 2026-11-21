package net.createplant.cloud;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.createplant.thermal.FluidIdentity;
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

    /** Styles registered by fluid id during mod construction, before the fluids themselves can be resolved. */
    private static final Map<String, Style> NAMED = new ConcurrentHashMap<>();

    private GasCloudStyles() { }

    public static void register(Fluid fluid, Style style) { Styles.BY_ID.put(id(fluid), style); }
    public static void register(String fluidId, Style style) { NAMED.put(fluidId, style); }

    static String id(Fluid fluid) {
        var key = BuiltInRegistries.FLUID.getKey(fluid);
        return key.getNamespace() + ":" + key.getPath().replace("flowing_", "");
    }
    /** A substance in a state it once had its own entry for keeps that entry's style, so gaseous water clouds as steam. */
    static String id(FluidStack stack) {
        for (var variant : FluidIdentity.variants(stack.getFluid()))
            if (find(id(variant)) != null && FluidIdentity.matches(stack, variant)) return id(variant);
        return id(stack.getFluid());
    }
    static Style find(String fluidId) {
        var style = Styles.BY_ID.get(fluidId);
        return style != null ? style : NAMED.get(fluidId);
    }
}
