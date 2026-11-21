package net.createplant.chemistry;

import java.util.Locale;
import net.createplant.CreatePlant;
import net.createplant.thermal.Grade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class ChemicalReadout {
    public static final String KEY = "chemical_ui." + CreatePlant.MOD_ID + ".";

    private ChemicalReadout() { }

    public static Component name(FluidStack fluid) {
        var name = fluid.getHoverName().copy();
        var info = FluidChemicalRegistry.of(fluid);
        if (info == null) return name;
        var formula = info.formula().equals("…") ? Component.translatable(KEY + "variable_composition") : Component.literal(info.formula());
        return name.append(Component.literal(" (").append(formula).append(")").withStyle(ChatFormatting.AQUA));
    }

    public static Component hazards(FluidStack fluid) {
        var info = FluidChemicalRegistry.of(fluid);
        if (info == null || info.hazards().isEmpty()) return null;
        var values = Component.empty();
        for (var hazard : info.hazards()) {
            if (!values.getSiblings().isEmpty()) values.append(" · ");
            values.append(Component.translatable(KEY + "hazard." + hazard.name().toLowerCase(Locale.ROOT)));
        }
        return values.withStyle(ChatFormatting.YELLOW);
    }

    public static Component contents(FluidStack fluid, int capacity) {
        return capacity > 0 ? Component.translatable(KEY + "contents", name(fluid), number(fluid.getAmount()), number(capacity))
            : Component.translatable(KEY + "amount", name(fluid), number(fluid.getAmount()));
    }

    /** A row of boxes, one per grade: green where the equipment is rated, a diamond at the current grade, then the current grade with its range. */
    public static Component heat(Grade.Heat current, Grade.Heat minimum, Grade.Heat maximum) {
        return stage("heat", current.ordinal(), minimum == null ? -1 : minimum.ordinal(), maximum == null ? -1 : maximum.ordinal(), Grade.Heat.values().length, current.detail());
    }

    public static Component pressure(Grade.Pressure current, Grade.Pressure minimum, Grade.Pressure maximum) {
        return stage("pressure", current.ordinal(), minimum == null ? -1 : minimum.ordinal(), maximum == null ? -1 : maximum.ordinal(), Grade.Pressure.values().length, current.detail());
    }

    public static Component processHeat(int stage) {
        var current = Grade.Heat.values()[Math.clamp(stage - 1, 0, Grade.Heat.values().length - 1)];
        return heat(current, Grade.Heat.VERY_HOT, Grade.Heat.VERY_HOT);
    }

    private static Component stage(String kind, int current, int minimum, int maximum, int steps, Component label) {
        MutableComponent bar = Component.empty();
        for (int i = 0; i < steps; i++) {
            boolean admissible = minimum >= 0 && i >= minimum && i <= maximum;
            boolean marker = current == i;
            var color = admissible ? ChatFormatting.GREEN : marker ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
            if (i > 0) bar.append(" ");
            bar.append(Component.literal(marker ? "◆" : admissible ? "■" : "□").withStyle(color));
        }
        return Component.translatable(KEY + kind + "_bar", bar, label);
    }

    public static Component warning(Component reason) {
        return Component.translatable(KEY + "warning", reason).withStyle(ChatFormatting.GOLD);
    }

    private static String number(int amount) { return String.format(Locale.ROOT, "%,d", amount); }
}
