package net.createplant.logistics.arm;

import net.createplant.logistics.block.entity.IndustrialArmBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Particles at the tool tip of a working arm; the beads and wires themselves are drawn by the jig renderer. */
public final class ArmEffects {
    private ArmEffects() {}

    public static void arm(IndustrialArmBlockEntity be) {
        Vec3 tip = be.tip;
        if (be.toolType() != ArmTool.WELDING_TORCH || tip == null || be.getSpeed() == 0) return;
        var level = be.getLevel();
        RandomSource r = level.random;
        for (int i = 0; i < 2; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y - .05, tip.z, (r.nextDouble() - .5) * .25, r.nextDouble() * .2, (r.nextDouble() - .5) * .25);
        if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 0, .03, 0);
    }
}
