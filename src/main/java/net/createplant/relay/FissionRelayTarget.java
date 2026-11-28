package net.createplant.relay;

import net.createplant.control.RelayAlu;
import net.createplant.control.RelayTarget;
import net.createplant.nuclear.ReactorCore;
import net.createplant.nuclear.ReactorCores;
import net.createplant.nuclear.ReactorPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Relay target for a formed RBMK core: the rod bank program and its lease on the core. */
public final class FissionRelayTarget implements RelayTarget {
    private double rodTarget = -1;
    private ReactorCore core;

    /**
     * Rod bank target in tenths of a percent, the relay version of ReactorAutomaticControl: hard limits first, then hold
     * the power at the target share of nominal using the reactivity margin. Inputs: rod 0..1000, hottest fuel K/10,
     * outlet K/2, flow in quarter mB/t, power in MW, load in % of the setpoint, reactivity in units of 1e-4.
     */
    public static int program(RelayAlu alu, int rod, int fuel, int outlet, int flow, int powerMw, int load, int reactivity, boolean scram) {
        int full = 1000;
        if (scram || alu.ge(fuel, (int) (ReactorPhysics.ALARM_KELVIN / 10))) return full;
        boolean lowFlow = alu.ge(powerMw, 2) && !alu.ge(flow, 1);
        if (alu.ge(fuel, 185) || alu.ge(outlet, 305) || lowFlow) return alu.min(full, alu.add(rod, 80));
        int coolantAlarm = (int) (ReactorPhysics.COOLANT_ALARM_KELVIN / 2);
        if (alu.ge(fuel, 170) || alu.ge(outlet, coolantAlarm)) return alu.min(full, alu.add(rod, 30));
        // Wanted reactivity: 0.0025 * (1 - load), clamped to one nominal either way; the quarter is a two-bit shift.
        int wanted = RelayAlu.shift(alu.max(-100, alu.min(100, alu.sub(100, load))), 2);
        int error = alu.sub(reactivity, wanted);
        int size = alu.ge(error, 0) ? error : alu.sub(0, error);
        int step = alu.max(1, alu.min(10, RelayAlu.shift(size, 2)));
        if (alu.ge(load, 103) || alu.ge(error, 3)) return alu.min(full, alu.add(rod, step));
        if (!alu.ge(load, 98) && !alu.ge(error, -2) && !alu.ge(fuel, 165) && !alu.ge(outlet, coolantAlarm) && alu.ge(flow, 2))
            return alu.max(0, alu.sub(rod, step));
        return rod;
    }

    private static ReactorCore lead(Level level, BlockPos computer, int range) {
        BlockPos controller = ReactorCores.nearest(level, computer, range);
        return controller != null && level.getBlockEntity(controller) instanceof net.createplant.block.entity.FuelChannelBlockEntity lead ? lead.core() : null;
    }

    @Override public boolean acquire(Level level, BlockPos computer, int range) {
        core = lead(level, computer, range);
        if (core == null || !core.formed()) { core = null; return false; }
        return true;
    }

    @Override public String step(RelayAlu alu, BlockPos computer, int targetPercent) {
        if (rodTarget < 0) rodTarget = Math.clamp(Math.round(core.rodInsertion() * 100), 0, 100);
        double flow = 0; var bottoms = core.fuelBottoms();
        for (BlockPos bottom : bottoms) flow += core.columnFlow(bottom);
        flow /= Math.max(1, bottoms.size());
        double setpoint = core.nominalWatts() * targetPercent / 100d;
        int tenths = program(alu, RelayAlu.input(rodTarget * 10), RelayAlu.input(core.hottestKelvin() / 10),
            RelayAlu.input(core.averageCoolantKelvin() / 2), RelayAlu.input(flow * 4), RelayAlu.input(core.power() / 1e6),
            RelayAlu.input(setpoint > 0 ? core.power() / setpoint * 100 : 0), RelayAlu.input(core.reactivity() * 1e4), core.scram());
        rodTarget = tenths / 10d;
        if (!core.setAutomaticTarget(computer, rodTarget)) { rodTarget = -1; return "busy"; }
        return core.scram() ? "reactor_scram" : "reactor";
    }

    @Override public void renewLease(Level level, BlockPos computer, int range) {
        if (rodTarget < 0) return;
        var lead = lead(level, computer, range);
        if (lead != null) lead.setAutomaticTarget(computer, rodTarget);
    }

    @Override public void release(Level level, BlockPos computer, int range) {
        rodTarget = -1;
        var lead = lead(level, computer, range);
        if (lead != null) lead.clearAutomaticTarget(computer);
    }
}
