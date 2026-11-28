package net.createplant.relay;

import net.createplant.block.entity.FusionReactorControllerBlockEntity;
import net.createplant.block.entity.FusionReactorControllerBlockEntity.Command;
import net.createplant.control.RelayAlu;
import net.createplant.control.RelayTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Relay target for a formed fusion reactor: the start-up and hold sequence. */
public final class FusionRelayTarget implements RelayTarget {
    private FusionReactorControllerBlockEntity fusion;

    /** One pass of the fusion start-up and hold sequence; returns the status key. */
    public static String program(RelayAlu alu, FusionReactorControllerBlockEntity f, int wanted) {
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

    /** One step of the fusion start-up / hold sequence on a fresh ALU; returns the status key. */
    public static String fusionStep(FusionReactorControllerBlockEntity f, int wanted) {
        return program(new RelayAlu(), f, wanted);
    }

    @Override public boolean acquire(Level level, BlockPos computer, int range) {
        fusion = null; double bestDistance = (double) range * range * 4;
        for (BlockPos pos : FusionReactorControllerBlockEntity.controllers(level)) {
            double d = pos.distSqr(computer);
            if (d < bestDistance && level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof FusionReactorControllerBlockEntity f && f.formed()) {
                fusion = f; bestDistance = d;
            }
        }
        return fusion != null;
    }

    @Override public String step(RelayAlu alu, BlockPos computer, int targetPercent) { return program(alu, fusion, targetPercent); }
    @Override public void renewLease(Level level, BlockPos computer, int range) { }
    @Override public void release(Level level, BlockPos computer, int range) { }
}
