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

    public static Component heat(Grade.Heat current, Grade.Heat minimum, Grade.Heat maximum) {
        return stage("heat", current.ordinal(), minimum == null ? -1 : minimum.ordinal(), maximum == null ? -1 : maximum.ordinal(), false);
    }

    public static Component pressure(Grade.Pressure current, Grade.Pressure minimum, Grade.Pressure maximum) {
        return stage("pressure", current.ordinal() * 2, minimum == null ? -1 : minimum.ordinal() * 2,
            maximum == null ? -1 : maximum.ordinal() * 2, true);
    }

    public static Component processHeat(int stage) { return stage("heat", stage - 1, 4, 4, false); }

    private static Component stage(String kind, int current, int minimum, int maximum, boolean alternate) {
        MutableComponent bar = Component.empty();
        for (int i = 0; i < 5; i++) {
            boolean admissible = minimum >= 0 && i >= minimum && i <= maximum && (!alternate || i % 2 == 0);
            boolean marker = current == i;
            var color = admissible ? ChatFormatting.GREEN : marker ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY;
            if (i > 0) bar.append(" ");
            bar.append(Component.literal(marker ? "◆" : admissible ? "■" : "□").withStyle(color));
        }
        return Component.translatable(KEY + kind + "_bar", bar, current + 1);
    }

    public static Component warning(Component reason) {
        return Component.translatable(KEY + "warning", reason).withStyle(ChatFormatting.GOLD);
    }

    private static String number(int amount) { return String.format(Locale.ROOT, "%,d", amount); }
}
