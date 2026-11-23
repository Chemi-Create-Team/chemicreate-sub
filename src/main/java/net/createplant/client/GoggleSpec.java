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
import net.minecraft.locale.Language;
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

    /** The kind of stop a machine's status word means; anything else is shown with its own text. */
    private static Reason classify(String status) {
        return switch (status) {
            case "fouled", "blocked" -> Reason.MAINTENANCE;
            case "hatch", "lid" -> Reason.HATCH;
            case "output", "gas_output", "gas_full", "products_full" -> Reason.OUTPUT;
            case "feed", "no_feed", "idle", "input" -> Reason.FEED;
            case "materials", "mixed" -> Reason.RECIPE;
            case "speed", "no_speed", "power", "no_power", "energy" -> Reason.POWER;
            case "heat", "heat_required", "preheat" -> Reason.HEAT;
            case "pressure" -> Reason.PRESSURE;
            default -> Reason.OTHER;
        };
    }

    private static Reason reason(LiddedVesselBlockEntity vessel) {
        if (vessel.open()) return Reason.HATCH;
        return classify(vessel.status());
    }

    /** A machine status's own text under the first key that has a translation, else the word itself with its underscores opened. */
    private static Component statusText(String status, String... prefixes) {
        for (var prefix : prefixes) if (Language.getInstance().has(prefix + status)) return Component.translatable(prefix + status);
        return Component.literal(status.replace('_', ' '));
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
            lines.add(Component.translatable(K + "stopped").withStyle(ChatFormatting.GOLD).append(label(": ")).append(reasonText(reason, statusText(vessel.status(), "reactor.create_refinery_n_reactors.status."), heated(vessel)).withStyle(ChatFormatting.GOLD)));
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
        // Equipment: the hatch and, under Shift, the size.
        var hatchKey = vessel.getWidth() == 1 ? "fixed" : vessel.open() ? "open" : "closed";
        var equipment = line(Component.translatable(K + "equipment").getString(),
            Component.translatable(K + "hatch." + hatchKey).withStyle(reason == Reason.HATCH ? ChatFormatting.GOLD : ChatFormatting.WHITE));
        if (shift) equipment.append(bar()).append(Component.translatable(K + "structure", vessel.getWidth(), vessel.getHeight()).withStyle(ChatFormatting.WHITE));
        lines.add(equipment);
    }

    private static MutableComponent reasonText(Reason reason, Component other, boolean superheat) {
        return switch (reason) {
            case POWER -> Component.translatable(K + "reason.power", 32);
            case HEAT -> Component.translatable(K + "reason.heat", Component.translatable(K + "temperature." + (superheat ? "superheated" : "hot")));
            case PRESSURE -> Component.translatable(K + "reason.pressure", Component.translatable(K + "pressure.high"));
            case OTHER -> other.copy();
            default -> Component.translatable(K + "reason." + reason.name().toLowerCase(Locale.ROOT));
        };
    }

    /** Line one, and under it the target reaction of a stopped machine that has one. */
    private static void header(List<Component> lines, Component equation, int percent, Reason reason, Component other, boolean superheat) {
        if (reason == null) {
            var head = Component.translatable(K + "running").withStyle(ChatFormatting.GREEN);
            if (equation != null) head.append(label(": ")).append(equation.copy().withStyle(ChatFormatting.WHITE));
            if (percent >= 0) head.append(label(" (" + percent + "%)"));
            lines.add(head);
            return;
        }
        lines.add(Component.translatable(K + "stopped").withStyle(ChatFormatting.GOLD).append(label(": ")).append(reasonText(reason, other, superheat).withStyle(ChatFormatting.GOLD)));
        if (equation != null) lines.add(line(Component.translatable(K + "target").getString(), equation.copy().withStyle(ChatFormatting.WHITE)));
    }

    /** The first filled bank as the stock, the other filled banks as one short line each. */
    private static void banks(List<Component> lines, List<FluidStack> fluids, List<Integer> capacities, boolean shift) {
        int first = -1;
        for (int i = 0; i < fluids.size() && first < 0; i++) if (!fluids.get(i).isEmpty()) first = i;
        if (first < 0) { stock(lines, List.of(), capacities.isEmpty() ? 0 : capacities.getFirst(), shift); return; }
        stock(lines, List.of(fluids.get(first)), capacities.get(first), shift);
        for (int i = first + 1; i < fluids.size(); i++) {
            if (fluids.get(i).isEmpty()) continue;
            lines.add(Component.literal(INDENT + "  ").append(white(name(fluids.get(i), shift))).append(label(": ")).append(count(fluids.get(i).getAmount(), capacities.get(i), shift)));
        }
    }

    // ---- other machines --------------------------------------------------------------------------------------------------------------

    /** The same layout for a stirred or stacked reactor machine, a reaction tower, a gasifier or a fractionating column; false for anything else. */
    public static boolean machine(net.minecraft.world.level.block.entity.BlockEntity owner, List<Component> lines, boolean shift) {
        if (owner instanceof net.createplant.block.entity.ColumnRingBlockEntity ring && ring.storey() != null) owner = ring.storey();
        if (owner instanceof net.createplant.block.entity.StackedReactorBlockEntity stack && stack.bottomStorey() != null) owner = stack.bottomStorey();
        if (owner instanceof net.createplant.block.entity.ReactorMachineBlockEntity machine && !(owner instanceof LiddedVesselBlockEntity) && machine.exchanger() == null
            && machine.module() != net.createplant.chemistry.ReactorModule.PREHEATER && !machine.horizontal()) return reactor(machine, lines, shift);
        if (owner instanceof net.createplant.block.entity.ReactionTowerBlockEntity tower) return tower(tower, lines, shift);
        if (owner instanceof net.createplant.block.entity.GasifierBlockEntity gasifier) return gasifier(gasifier, lines, shift);
        if (owner instanceof net.createplant.block.entity.FractionatingColumnBlockEntity column) return column(column, lines, shift);
        return false;
    }

    private static boolean reactor(net.createplant.block.entity.ReactorMachineBlockEntity machine, List<Component> lines, boolean shift) {
        boolean running = machine.formed() && machine.running();
        var reason = running ? null : classify(machine.status());
        lines.clear();
        header(lines, machine.batchEmpty() ? null : machine.reactionLabel(), Math.round(machine.progress() * 100 / Math.max(1, machine.cycleTicks())), reason,
            statusText(machine.status(), "reactor.create_refinery_n_reactors.status.", "reaction_tower.create_refinery_n_reactors.status."), false);
        var fluids = new ArrayList<FluidStack>();
        var capacities = new ArrayList<Integer>();
        for (int slot = 0; slot < machine.displayedFluidSlots(); slot++) { fluids.add(machine.displayedFluid(slot)); capacities.add(machine.getDisplayedCapacity(slot)); }
        banks(lines, fluids, capacities, shift);
        int rpm = Math.round(Math.abs(machine.rotationSpeed()));
        if (rpm > 0 || reason == Reason.POWER) lines.add(line(Component.translatable(K + "environment").getString(), label(Component.translatable(K + "power").getString() + ": "),
            Component.literal(rpm + " RPM").withStyle(reason == Reason.POWER ? ChatFormatting.GOLD : ChatFormatting.WHITE)));
        if (shift) lines.add(line(Component.translatable(K + "equipment").getString(), Component.translatable(K + "storeys", machine.sections()).withStyle(ChatFormatting.WHITE)));
        return true;
    }

    private static boolean tower(net.createplant.block.entity.ReactionTowerBlockEntity tower, List<Component> lines, boolean shift) {
        var status = tower.status();
        var recipe = tower.recipe();
        var reason = status.equals("running") ? null : classify(status);
        lines.clear();
        header(lines, recipe == null ? null : equation(recipe.inputs(), recipe.outputs()), tower.cycleTicks() > 0 ? Math.round(tower.progress() * 100f / tower.cycleTicks()) : -1, reason,
            statusText(status, "reaction_tower.create_refinery_n_reactors.status.", "reactor.create_refinery_n_reactors.status."),
            recipe != null && recipe.requiredHeat() == com.simibubi.create.content.processing.recipe.HeatCondition.SUPERHEATED);
        var fluids = new ArrayList<FluidStack>();
        var capacities = new ArrayList<Integer>();
        for (int bank = 0; bank < 4; bank++) { fluids.add(tower.contents(bank)); capacities.add(tower.contentsCapacity(bank)); }
        banks(lines, fluids, capacities, shift);
        lines.add(line(Component.translatable(K + "environment").getString(), white(tower.heaterGrade().label().getString()), label(" / "),
            white(net.createplant.thermal.Grade.Pressure.of(tower.pressure()).label().getString())));
        int[] trays = tower.trayCounts();
        if (trays[0] > 0) lines.add(line(Component.translatable(K + "equipment").getString(), Component.translatable(K + "catalyst", trays[1], trays[0], trays[2]).withStyle(
            reason == Reason.OTHER && status.equals("catalyst") ? ChatFormatting.GOLD : ChatFormatting.WHITE)));
        if (shift) lines.add(line(Component.translatable(K + "equipment").getString(), Component.translatable(K + "storeys", tower.layers()).withStyle(ChatFormatting.WHITE)));
        return true;
    }

    private static boolean gasifier(net.createplant.block.entity.GasifierBlockEntity gasifier, List<Component> lines, boolean shift) {
        var status = gasifier.status();
        var reason = status.equals("running") ? null : classify(status);
        lines.clear();
        header(lines, null, -1, reason, net.createplant.block.entity.GasifierBlockEntity.message(status), true);
        if (!gasifier.formed()) return true;
        var fluids = new ArrayList<FluidStack>();
        var capacities = new ArrayList<Integer>();
        for (int bank : new int[] {2, 1}) { fluids.add(gasifier.fluid(bank)); capacities.add(gasifier.capacity(bank)); }
        banks(lines, fluids, capacities, shift);
        lines.add(line(Component.translatable(K + "environment").getString(), white(net.createplant.thermal.Grade.Heat.of(gasifier.temperature() + 273.15).label().getString())));
        return true;
    }

    private static boolean column(net.createplant.block.entity.FractionatingColumnBlockEntity column, List<Component> lines, boolean shift) {
        var port = lines.isEmpty() ? null : lines.getLast();
        var status = column.status();
        var reason = column.running() ? null : classify(status);
        lines.clear();
        header(lines, null, column.running() ? Math.round(column.progress() * 100 / Math.max(1, column.cycleTicks())) : -1, reason,
            statusText(status, "distillation.create_refinery_n_reactors.status."), false);
        var fluids = new ArrayList<FluidStack>();
        var capacities = new ArrayList<Integer>();
        for (int slot = 0; slot < net.createplant.chemistry.DistillationProfile.SLOTS; slot++) { var fluid = column.content(slot); if (!fluid.isEmpty()) { fluids.add(fluid); capacities.add(column.capacity()); } }
        banks(lines, fluids, capacities, shift);
        if (shift) lines.add(line(Component.translatable(K + "equipment").getString(), Component.translatable(K + "structure", column.getWidth(), column.getHeight()).withStyle(ChatFormatting.WHITE)));
        if (port != null) lines.add(Component.literal(INDENT).append(port.copy().withStyle(ChatFormatting.WHITE)));
        return true;
    }

    /** "A + B → C + D" from the formulas of fluid stacks. */
    public static Component equation(List<FluidStack> in, List<FluidStack> out) {
        return Component.literal(String.join(" + ", in.stream().map(GoggleSpec::formula).toList()) + " → " + String.join(" + ", out.stream().map(GoggleSpec::formula).toList()));
    }

    static String formula(FluidStack fluid) {
        var info = FluidChemicalRegistry.of(fluid);
        return info == null || info.formula().equals("…") ? fluid.getHoverName().getString() : info.formula();
    }

    // ---- electrolysis, turbines, fusion, the reactor console and the pipe gauge -----------------------------------------------------------

    private static boolean firstIs(List<Component> lines, String keyPrefix) {
        return !lines.isEmpty() && lines.getFirst().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text && text.getKey().startsWith(keyPrefix);
    }

    /** Puts the layout's first line over a machine whose own tooltip starts with a status line, and sets its other lines under it. */
    public static boolean status(net.minecraft.world.level.block.entity.BlockEntity owner, List<Component> lines, boolean shift) {
        boolean running;
        Component text;
        if (owner instanceof net.createplant.block.entity.ElectrolysisControllerBlockEntity cell && firstIs(lines, "electrolysis.status.")) {
            running = cell.running() > 0;
            text = Component.translatable("electrolysis.status." + cell.status());
        } else if (owner instanceof net.createplant.block.entity.SteamTurbineBlockEntity turbine && firstIs(lines, "turbine.create_refinery_n_reactors.state")) {
            var state = turbine.thermodynamics().status();
            running = state.equals("running");
            text = Component.translatable("turbine.create_refinery_n_reactors." + state);
        } else if (owner instanceof net.createplant.block.entity.FusionReactorControllerBlockEntity fusion && firstIs(lines, "fusion_reactor.create_refinery_n_reactors.goggle.status")) {
            running = fusion.status().equals("burning");
            text = Component.translatable("fusion_reactor.create_refinery_n_reactors.status." + fusion.status());
        } else if (owner instanceof net.createplant.block.entity.ReactorControlConsoleBlockEntity console && firstIs(lines, "reactor_control_console.create_refinery_n_reactors.status.")) {
            running = console.linked() && java.util.Set.of("critical", "supercritical", "prompt_critical", "unstable").contains(console.reactorStatus());
            text = console.linked() ? Component.translatable("reactor_core.create_refinery_n_reactors.status." + console.reactorStatus()) : lines.getFirst();
        } else if (owner instanceof net.createplant.block.entity.PipeGaugeBlockEntity && lines.size() > 1) {
            lines.removeFirst();
            indentRest(lines);
            return true;
        } else return false;
        lines.removeFirst();
        var rest = new ArrayList<>(lines);
        lines.clear();
        // A status that already begins "Stopped:" does not need the layout's own.
        var plain = text.getString().replaceFirst("^(Stopped|停止中|已停止)[:：]\\s*", "");
        header(lines, running ? text : null, -1, running ? null : Reason.OTHER, running || plain.equals(text.getString()) ? text : Component.literal(plain), false);
        for (var line : rest) lines.add(line.getString().isBlank() ? line : Component.literal(INDENT).append(line));
        return true;
    }

    /** The first line stays where the icon is; the rest stand under it. */
    private static void indentRest(List<Component> lines) {
        for (int i = 1; i < lines.size(); i++) lines.set(i, Component.literal(INDENT).append(lines.get(i)));
    }
}
