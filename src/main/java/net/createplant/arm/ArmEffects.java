package net.createplant.arm;

import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Particles at the tool tip of a working arm; the beads, wires and film themselves are drawn by the jig renderer. */
public final class ArmEffects {
    private ArmEffects() {}

    public static void arm(IndustrialArmBlockEntity be) {
        ArmTool t = be.toolType();
        Vec3 tip = be.tip;
        if (t == null || tip == null || be.getSpeed() == 0) return;
        var level = be.getLevel();
        RandomSource r = level.random;
        switch (t) {
            case WELDING_TORCH -> {
                for (int i = 0; i < 2; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y - .05, tip.z, (r.nextDouble() - .5) * .25, r.nextDouble() * .2, (r.nextDouble() - .5) * .25);
                if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0, .03, 0);
            }
            case LASER_CUTTER -> {
                if (r.nextInt(2) == 0) level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y - .05, tip.z, (r.nextDouble() - .5) * .02, .04, (r.nextDouble() - .5) * .02);
            }
            case SPRAY_NOZZLE -> level.addParticle(ParticleTypes.FALLING_WATER, tip.x + (r.nextDouble() - .5) * .1, tip.y - .05, tip.z + (r.nextDouble() - .5) * .1, 0, -.05, 0);
            case WIRING_HEAD -> {
                if (r.nextInt(6) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y - .05, tip.z, 0, .05, 0);
            }
            default -> {}
        }
    }
}
