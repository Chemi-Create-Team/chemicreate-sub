package net.createplant.block;

import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** The chime and sparks that mark a completed multiblock, matching the tank formation effects. */
public final class FormationEffects {
    private FormationEffects() { }

    public static void formed(Level level, Collection<BlockPos> parts) {
        if (parts.isEmpty()) return;
        int x1 = Integer.MAX_VALUE, y1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, y2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
        for (BlockPos pos : parts) {
            x1 = Math.min(x1, pos.getX());y1 = Math.min(y1, pos.getY());z1 = Math.min(z1, pos.getZ());
            x2 = Math.max(x2, pos.getX());y2 = Math.max(y2, pos.getY());z2 = Math.max(z2, pos.getZ());
        }
        formed(level, new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
    }

    public static void formed(Level level, BlockPos min, BlockPos max) {
        if (!(level instanceof ServerLevel server)) return;
        double x = (min.getX() + max.getX() + 1) / 2d, y = (min.getY() + max.getY() + 1) / 2d, z = (min.getZ() + max.getZ() + 1) / 2d;
        server.playSound(null, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, .8f, 1.2f);
        server.playSound(null, x, y, z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, .7f, .8f);
        for (double edgeX : new double[] {min.getX(), max.getX() + 1}) for (double edgeZ : new double[] {min.getZ(), max.getZ() + 1})
            server.sendParticles(ParticleTypes.END_ROD, edgeX, y, edgeZ, 5, 0, (max.getY() - min.getY() + 1) / 3d, 0, .02);
        server.sendParticles(ParticleTypes.CLOUD, x, max.getY() + 1, z, 8, (max.getX() - min.getX() + 1) / 3d, .1, (max.getZ() - min.getZ() + 1) / 3d, .02);
    }

    /** Remembers whether a machine has already announced its structure, and stays quiet for a few seconds after a chunk load. */
    public static final class Announcer {
        private boolean announced, fresh;
        private long quietUntil;

        public void loaded() { fresh = true; }

        public void update(Level level, boolean formed, Collection<BlockPos> parts) {
            if (level == null || level.isClientSide) return;
            if (fresh) { fresh = false;quietUntil = level.getGameTime() + 100; }
            if (!formed) { announced = false;return; }
            if (announced) return;
            announced = true;
            if (level.getGameTime() >= quietUntil) formed(level, parts);
        }
    }
}
