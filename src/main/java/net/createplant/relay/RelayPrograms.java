package net.createplant.relay;

import net.createplant.control.RelayAlu;
import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity.Command;
import net.createplant.nuclear.ReactorPhysics;

/**
 * The relay computer's control programs. Sensors come in as scaled integers (meter contacts); every sum, difference and
 * comparison is made on the {@link RelayAlu}. Thresholds are wired constants.
 */
public final class RelayPrograms {
    private RelayPrograms() { }

    /**
     * Rod bank target in tenths of a percent, the relay version of ReactorAutomaticControl: hard limits first, then hold
     * the power at the target share of nominal using the reactivity margin. Inputs: rod 0..1000, hottest fuel K/10,
     * outlet K/2, flow in quarter mB/t, power in MW, load in % of the setpoint, reactivity in units of 1e-4.
     */
    public static int fission(RelayAlu alu, int rod, int fuel, int outlet, int flow, int powerMw, int load, int reactivity, boolean scram) {
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

    /** One pass of the fusion start-up and hold sequence; returns the status key. */
    public static String fusion(RelayAlu alu, FusionReactorControllerBlockEntity f, int wanted) {
        if (f.emergency()) return "fusion_emergency";
        if (f.quench()) { f.command(Command.PLASMA, false); return "fusion_quench"; }
        f.command(Command.VACUUM, true);
        // Vacuum in units of 1e-7 Pa: ready at or below 105 (1.05e-5 Pa).
        boolean burning = f.fusionWatts() > 0;
        if (!burning && alu.ge(RelayAlu.input(f.baseVacuumPascals() * 1e7), 106)) return "fusion_vacuum";
        f.command(Command.CRYOGENICS, true);
        if (alu.ge(RelayAlu.input(f.coilKelvin() * 10), 51)) { f.command(Command.PLASMA, false); return "fusion_cooling"; }
        f.command(Command.MAGNETS, true);
        if (!alu.ge(RelayAlu.input(Math.floor(f.magnetCharge() * 100)), 98)) return "fusion_charging";
        f.command(Command.PLASMA, true);
        int current = f.targetPercent();
        if (alu.ge(RelayAlu.input(f.blanketKelvin()), 601)) f.setTargetPercent(alu.sub(current, 10));
        else {
            int delta = alu.max(-5, alu.min(5, alu.sub(wanted, current)));
            f.setTargetPercent(alu.add(current, delta));
        }
        return "fusion";
    }
}
