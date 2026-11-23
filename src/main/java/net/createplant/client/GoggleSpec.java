package net.createplant.client;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.FluidChemicalRegistry;
import net.createplant.mixture.MixtureHelper;
import net.createplant.thermal.FluidState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The engineer goggles' layout (docs/goggle-tooltip.md): line one says "running" or "stopped: the first thing to fix", then the stock in one
 * place, the environment and the equipment. Formulas stand alone until Shift, which also shows exact millibuckets and the structure size.
 */
public final class GoggleSpec {
    private static final String K = "goggle_spec." + CreatePlant.MOD_ID + ".";
    private static final int LISTED = 5;
    /** Create's goggle icon takes the first four columns (the mixin adds them to the first line); the spec's two-space steps come after them. */
    private static final String INDENT = "      ";

    private GoggleSpec() { }

    // ---- numbers and names ---------------------------------------------------------------------------------------------------------

    /** 0-999 mB as they are, 1,000-9,999 mB in buckets to one decimal without a trailing .0, from 10,000 mB whole buckets with commas; Shift gives mB. */
    public static String amount(long mb, boolean shift) {
        if (shift || mb < 1000) return String.format(Locale.ROOT, "%,d mB", mb);
        return bucketsOf(mb) + " B";
    }

    private static String bucketsOf(long mb) {
        if (mb >= 10_000) return String.format(Locale.ROOT, "%,d", Math.round(mb / 1000d));
        var text = String.format(Locale.ROOT, "%.1f", mb / 1000d);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /** One decimal; a trace under 0.1% is "&lt;0.1%" rather than "0.0%". */
    public static String percent(long part, long total) {
        double share = total <= 0 ? 0 : part * 100d / total;
        return part > 0 && share < 0.05 ? "<0.1%" : String.format(Locale.ROOT, "%.1f%%", share);
    }

    /** The formula alone, or the name with the formula in brackets under Shift. */
    public static String name(FluidStack fluid, boolean shift) {
        var info = FluidChemicalRegistry.of(fluid);
        var formula = info == null || info.formula().equals("…") ? "" : info.formula();
        if (formula.isEmpty()) return fluid.getHoverName().getString();
        return shift ? fluid.getHoverName().getString() + " (" + formula + ")" : formula;
    }

    private static MutableComponent label(String text) { return Component.literal(text).withStyle(ChatFormatting.GRAY); }

    private static MutableComponent white(String text) { return Component.literal(text).withStyle(ChatFormatting.WHITE); }

    private static MutableComponent line(String label, MutableComponent... values) {
        var line = Component.literal(INDENT).append(label(label + ": "));
        for (var value : values) line.append(value);
        return line;
    }

    private static MutableComponent bar() { return label(" | "); }

    // ---- the stock -----------------------------------------------------------------------------------------------------------------

    /** "Stock: 8 / 144 B (H2SO4)"; under Shift in mB with the fluid listed below; a mixture lists its components by share. */
    public static void stock(List<Component> lines, List<FluidStack> parts, long capacity, boolean shift) {
        var present = parts.stream().filter(part -> !part.isEmpty()).toList();
        long total = present.stream().mapToLong(FluidStack::getAmount).sum();
        var title = Component.translatable(K + "stock").getString();
        var head = (lines.isEmpty() ? Component.empty().append(label(title + ": ")) : line(title)).append(count(total, capacity, shift));
        var components = new ArrayList<FluidStack>();
        for (var part : present) {
            if (MixtureHelper.composite(part)) components.addAll(MixtureHelper.components(part));
            else components.add(part);
        }
        var merged = new ArrayList<FluidStack>();
        for (var part : components) {
            var same = merged.stream().filter(seen -> seen.getFluid().isSame(part.getFluid())).findFirst();
            if (same.isPresent()) same.get().grow(part.getAmount());
            else merged.add(part.copy());
        }
        merged.sort(Comparator.comparingInt(FluidStack::getAmount).reversed());
        if (merged.size() == 1 && !shift) head.append(label(" (")).append(white(name(merged.getFirst(), false))).append(label(")"));
        lines.add(head);
        if (total <= 0 || merged.size() == 1 && !shift) return;
        int shown = shift ? merged.size() : Math.min(LISTED, merged.size());
        for (int i = 0; i < shown; i++) {
            var part = merged.get(i);
            var row = Component.literal(INDENT + "  ").append(white(name(part, shift))).append(label(": "));
            row.append(shift ? white(amount(part.getAmount(), true)).append(label(" (" + percent(part.getAmount(), total) + ")")) : label(percent(part.getAmount(), total)));
            lines.add(row);
        }
        if (shown < merged.size()) lines.add(Component.literal(INDENT + "  ").append(Component.translatable(K + "more", merged.size() - shown)).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent count(long amount, long capacity, boolean shift) {
        boolean buckets = !shift && capacity >= 1000 && (amount == 0 || amount >= 1000);
        var value = white(buckets ? amount == 0 ? "0" : bucketsOf(amount) : amount(amount, shift));
        if (capacity <= 0) return value;
        return value.append(label(" / ")).append(label(buckets ? bucketsOf(capacity) + " B" : amount(capacity, shift)));
    }

    /** "Running" for a machine that is mid-process, or null. */
    public static Component running(net.minecraft.world.level.block.entity.BlockEntity owner) {
        boolean running = owner instanceof net.createplant.block.entity.ReactionTowerBlockEntity tower ? tower.status().equals("running")
            : owner instanceof net.createplant.block.entity.ReactorMachineBlockEntity machine && machine.formed() && machine.running();
        return running ? Component.translatable(K + "running").withStyle(ChatFormatting.GREEN) : null;
    }

    /** A storage-only tank or a gas store: the stock and nothing about reactions. */
    public static void storage(List<Component> lines, List<FluidStack> parts, long capacity, boolean shift) {
        lines.clear();
        stock(lines, parts, capacity, shift);
    }

    // ---- the lidded vessel ---------------------------------------------------------------------------------------------------------

    private enum Reason { HATCH, DRAIN, MAINTENANCE, OUTPUT, FEED, RECIPE, POWER, HEAT, PRESSURE, OTHER }

    private static Reason reason(LiddedVesselBlockEntity vessel) {
        var status = vessel.status();
        if (vessel.open()) return Reason.HATCH;
        if (vessel.fouling().drainOpen()) return Reason.DRAIN;
        if (status.equals("fouled")) return Reason.MAINTENANCE;
        return switch (status) {
            case "output", "gas_full" -> Reason.OUTPUT;
            case "feed", "idle" -> Reason.FEED;
            case "materials" -> Reason.RECIPE;
            case "speed" -> Reason.POWER;
            case "heat" -> Reason.HEAT;
            case "pressure" -> Reason.PRESSURE;
            default -> Reason.OTHER;
        };
    }

    private static boolean heated(LiddedVesselBlockEntity vessel) { return vessel.currentBurnerHeat().isAtLeast(BlazeBurnerBlock.HeatLevel.KINDLED); }

    /** Replaces the lines with the layout for the vessel's controller; a plain storage vessel gets the stock lines alone. */
    public static void vessel(LiddedVesselBlockEntity vessel, List<Component> lines, boolean shift) {
        lines.clear();
        var stocks = new ArrayList<FluidStack>();
        stocks.add(vessel.getTankInventory().getFluid());
        for (int slot = 0; slot < vessel.displayedFluidSlots(); slot++) stocks.add(vessel.displayedFluid(slot));
        long capacity = vessel.getTankInventory().getCapacity();
        boolean gasLayers = vessel.showGasComposition() && !vessel.gasLayers().isEmpty();
        var shown = gasLayers ? vessel.gasLayers() : stocks;
        if (gasLayers) capacity = ((net.createplant.thermal.ThermalFluidTank) vessel.getTankInventory()).gasCapacity();
        if (vessel.module() == net.createplant.chemistry.ReactorModule.NONE && !vessel.running() && !vessel.status().equals("heat")) {
            stock(lines, shown, capacity, shift);
            return;
        }
        boolean running = vessel.running();
        var reason = running ? null : reason(vessel);
        var equation = vessel.reactionLabel();
        if (running) {
            int percent = Math.round(vessel.progress() * 100 / Math.max(1, vessel.cycleTicks()));
            lines.add(Component.translatable(K + "running").withStyle(ChatFormatting.GREEN).append(label(": ")).append(equation.copy().withStyle(ChatFormatting.WHITE))
                .append(label(" (" + percent + "%)")));
        } else {
            lines.add(Component.translatable(K + "stopped").withStyle(ChatFormatting.GOLD).append(label(": ")).append(reasonText(vessel, reason).withStyle(ChatFormatting.GOLD)));
            if (!vessel.batchEmpty()) lines.add(line(Component.translatable(K + "target").getString(), equation.copy().withStyle(ChatFormatting.WHITE)));
        }
        stock(lines, shown, capacity, shift);
        // Environment: temperature, pressure and drive.
        var heat = vessel.currentBurnerHeat();
        var temperature = heat == BlazeBurnerBlock.HeatLevel.SEETHING ? "superheated" : heated(vessel) ? "hot" : "ambient";
        boolean pressurised = stocks.stream().anyMatch(part -> !part.isEmpty() && FluidState.pressure(part) > FluidState.pressureLevel(1));
        int rpm = Math.round(Math.abs(vessel.rotationSpeed()));
        var environment = line(Component.translatable(K + "environment").getString(), white(Component.translatable(K + "temperature." + temperature).getString()),
            label(" / "), white(Component.translatable(K + "pressure." + (pressurised ? "high" : "normal")).getString()), bar(), label(Component.translatable(K + "power").getString() + ": "),
            Component.literal(rpm + " RPM").withStyle(reason == Reason.POWER ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        lines.add(environment);
        // Equipment: hatch, drain, dirt and, under Shift, the size.
        var hatchKey = vessel.getWidth() == 1 ? "fixed" : vessel.open() ? "open" : "closed";
        var drain = vessel.fouling().drainOpen();
        var equipment = line(Component.translatable(K + "equipment").getString(),
            Component.translatable(K + "hatch." + hatchKey).withStyle(reason == Reason.HATCH ? ChatFormatting.GOLD : ChatFormatting.WHITE), bar(),
            Component.translatable(K + "drain." + (drain ? "open" : "closed")).withStyle(reason == Reason.DRAIN ? ChatFormatting.GOLD : ChatFormatting.WHITE), bar(),
            Component.translatable(K + "dirt", Math.round(vessel.fouling().foulingFraction() * 100)).withStyle(reason == Reason.MAINTENANCE ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        if (shift) equipment.append(bar()).append(Component.translatable(K + "structure", vessel.getWidth(), vessel.getHeight()).withStyle(ChatFormatting.WHITE));
        lines.add(equipment);
    }

    private static MutableComponent reasonText(LiddedVesselBlockEntity vessel, Reason reason) {
        return switch (reason) {
            case POWER -> Component.translatable(K + "reason.power", 32);
            case HEAT -> Component.translatable(K + "reason.heat", Component.translatable(K + "temperature." + (heated(vessel) ? "superheated" : "hot")));
            case PRESSURE -> Component.translatable(K + "reason.pressure", Component.translatable(K + "pressure.high"));
            case OTHER -> Component.translatable("reactor.create_refinery_n_reactors.status." + vessel.status());
            default -> Component.translatable(K + "reason." + reason.name().toLowerCase(Locale.ROOT));
        };
    }
}
