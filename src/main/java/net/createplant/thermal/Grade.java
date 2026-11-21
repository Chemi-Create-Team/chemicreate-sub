package net.createplant.thermal;

import java.util.Locale;
import net.createplant.CreatePlant;
import net.minecraft.network.chat.Component;

/**
 * Temperature and pressure as the player meets them: a few named grades instead of kelvin and bar. Fluids, pipes, tanks and recipes
 * are held to grades; the reactor and fusion cores keep their own internal temperatures.
 */
public final class Grade {
    private Grade() { }

    public enum Heat {
        CRYOGENIC, COLD, AMBIENT, HOT, VERY_HOT;

        /** The grade a temperature falls in: below 150 K, below 10 °C, below boiling water (373 K), below 500 °C, and above. */
        public static Heat of(double kelvin) {
            return kelvin < 150 ? CRYOGENIC : kelvin < 283.15 ? COLD : kelvin < 373 ? AMBIENT : kelvin < 773.15 ? HOT : VERY_HOT;
        }

        public Component label() { return Component.translatable("grade." + CreatePlant.MOD_ID + ".heat." + name().toLowerCase(Locale.ROOT)); }
        /** The name with the range it stands for, such as "Ambient (10 to 100 °C)". */
        public Component detail() { return Component.translatable("grade." + CreatePlant.MOD_ID + ".heat." + name().toLowerCase(Locale.ROOT) + ".detail"); }
    }

    public enum Pressure {
        ATMOSPHERIC, PRESSURISED, HIGH;

        /** The grade a pressure falls in: below 2 bar, below 20 bar, and above. */
        public static Pressure of(double bar) { return bar < 2 ? ATMOSPHERIC : bar < 20 ? PRESSURISED : HIGH; }
        public int storageRatio() { return switch(this) { case ATMOSPHERIC -> 1;case PRESSURISED -> 16;case HIGH -> 64; }; }

        public Component label() { return Component.translatable("grade." + CreatePlant.MOD_ID + ".pressure." + name().toLowerCase(Locale.ROOT)); }
        /** The name with the range it stands for, such as "Ambient (10 to 100 °C)". */
        public Component detail() { return Component.translatable("grade." + CreatePlant.MOD_ID + ".pressure." + name().toLowerCase(Locale.ROOT) + ".detail"); }
    }

    /** A fluid's grades as one line, as goggles and gauges show them. */
    public static Component describe(Heat heat, Pressure pressure) {
        return Component.translatable("grade." + CreatePlant.MOD_ID + ".fluid", heat.detail(), pressure.detail());
    }
}
