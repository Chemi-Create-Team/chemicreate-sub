package net.createplant.thermal;

import java.util.function.IntPredicate;
import net.createplant.CreatePlant;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The seventeen temperature and pressure levels drawn as one line of marks: a diamond on each of the five grades and three dots between,
 * the rated range in white, and the contents' level as a filled dot, green inside the range and red outside it.
 */
public final class GradeScale {
    private static final String KEY = "grade." + CreatePlant.MOD_ID + ".scale";

    private GradeScale() { }

    /** Both scales on one line; a negative level draws no contents. */
    public static Component line(Grade.Heat coldest, Grade.Heat hottest, Grade.Pressure strongest, int temperature, int pressure) {
        return Component.translatable(KEY, temperature(coldest, hottest, temperature), pressure(strongest, pressure));
    }

    /** A reading with no rated range, as on a pipe gauge: grey marks and the level in green. */
    public static Component reading(int level) {
        return marks(at -> at == level, level);
    }

    public static MutableComponent temperature(Grade.Heat coldest, Grade.Heat hottest, int current) {
        return marks(level -> {
            int grade = Grade.Heat.of(FluidState.kelvin(level)).ordinal();
            return grade >= coldest.ordinal() && grade <= hottest.ordinal();
        }, current);
    }

    public static MutableComponent pressure(Grade.Pressure strongest, int current) {
        return marks(level -> Grade.Pressure.of(FluidState.bar(level)).ordinal() <= strongest.ordinal(), current);
    }

    private static MutableComponent marks(IntPredicate rated, int current) {
        var line = Component.empty();
        for (int level = 0; level <= FluidState.MAX; level++) {
            boolean inside = rated.test(level);
            if (level == current) line.append(Component.literal("●").withStyle(inside ? ChatFormatting.GREEN : ChatFormatting.RED));
            else line.append(Component.literal(level % 4 == 0 ? "◆" : "·").withStyle(inside ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        return line;
    }
}
