package net.createplant.compat.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.createplant.block.entity.ProductionMonitor;
import net.createplant.block.entity.ProductionMonitor.Output;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

final class ProductionReadings {
    private static final String PREFIX = "production.create_refinery_n_reactors.";
    private static final List<Output> OUTPUTS = List.of(Output.FLUID, Output.ITEMS, Output.POWER);

    private ProductionReadings() { }

    static MachineReadings.Reading read(BlockEntity owner, String key) {
        var meter = ProductionMonitor.of(owner);
        if (meter == null) return null;
        var unit = switch (key) {
            case "production_fluid", "production_fluid_max" -> Output.FLUID;
            case "production_items", "production_items_max" -> Output.ITEMS;
            case "production_power", "production_power_max" -> Output.POWER;
            default -> null;
        };
        var rates = unit == null ? null : meter.read(unit);
        if (rates == null) return null;
        if (key.endsWith("_max")) return new MachineReadings.Reading(rates.maximum(), 0);
        return rates.current() < 0 ? null : new MachineReadings.Reading(rates.current(), rates.maximum());
    }

    static Component limit(BlockEntity owner) {
        var meter = ProductionMonitor.of(owner);
        return meter == null || meter.limit().isEmpty() ? Component.literal("--") : Component.translatable(PREFIX + "limit." + meter.limit());
    }

    static void append(BlockEntity owner, List<Component> tooltip) {
        var meter = ProductionMonitor.of(owner);
        if (meter == null) return;
        var summary = new ArrayList<Component>(4);
        for (var output : OUTPUTS) {
            var rates = meter.read(output);
            if (rates == null) continue;
            summary.add(Component.translatable(PREFIX + output.name().toLowerCase(Locale.ROOT),
                rates.current() < 0 ? Component.translatable(PREFIX + "measuring") : format(rates.current()), format(rates.maximum())));
        }
        if (!meter.limit().isEmpty()) summary.add(Component.translatable(PREFIX + "bottleneck", limit(owner)));
        if (summary.isEmpty()) return;
        while (tooltip.size() + summary.size() > 4) tooltip.removeLast();
        tooltip.addAll(summary);
    }

    private static String format(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount).replaceAll("\\.?0+$", "");
    }
}
