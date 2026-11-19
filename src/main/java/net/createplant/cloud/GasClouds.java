package net.createplant.cloud;

import java.util.List;
import java.util.WeakHashMap;
import net.createplant.registry.CESafety;
import net.createplant.thermal.SteamGrades;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GasClouds {
    public static final int DETECTOR_REACH = 4;
    private static final int MAX_PER_CHUNK = 8;
    private static final int REBUILDS_PER_TICK = 4;
    private static final WeakHashMap<ServerLevel, RebuildBudget> REBUILDS = new WeakHashMap<>();

    private static final class RebuildBudget {
        long tick = Long.MIN_VALUE;
        int used;
    }

    private GasClouds() { }

    public static boolean supported(FluidStack stack) { return SteamGrades.of(stack) != null; }

    public static void release(ServerLevel level, BlockPos pos, FluidStack stack) {
        if (!supported(stack) || !level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return;

        for (var cloud : in(level, new AABB(pos).inflate(1))) {
            if (cloud.compatible(stack) && cloud.touchesSource(pos)) {
                cloud.merge(stack);
                return;
            }
        }

        var chunk = new ChunkPos(pos);
        var area = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
            chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
        var local = level.getEntitiesOfClass(GasCloud.class, includeAnchors(area), cloud -> chunk.equals(cloud.chunkPosition()) && cloud.isAlive());
        if (local.size() >= MAX_PER_CHUNK) {
            GasCloud nearest = null;
            double closest = Double.MAX_VALUE;
            for (var cloud : local) {
                double distance = cloud.blockPosition().distSqr(pos);
                if (distance < closest && cloud.compatible(stack)) {
                    nearest = cloud;
                    closest = distance;
                }
            }
            if (nearest != null) nearest.merge(stack);
            return;
        }

        var cloud = new GasCloud(CESafety.GAS_CLOUD.get(), level);
        cloud.initialize(pos, stack);
        if (level.addFreshEntity(cloud) && takeRebuildSlot(level)) cloud.rebuild();
    }

    static boolean takeRebuildSlot(ServerLevel level) {
        var budget = REBUILDS.computeIfAbsent(level, ignored -> new RebuildBudget());
        long time = level.getGameTime();
        if (budget.tick != time) {
            budget.tick = time;
            budget.used = 0;
        }
        if (budget.used >= REBUILDS_PER_TICK) return false;
        budget.used++;
        return true;
    }

    public static List<GasCloud> in(Level level, AABB area) {
        return level.getEntitiesOfClass(GasCloud.class, includeAnchors(area),
            cloud -> cloud.isAlive() && cloud.getBoundingBox().intersects(area));
    }

    private static AABB includeAnchors(AABB box) {
        // Entity sections are indexed by the release position even when the visible cloud extends past it.
        return box.inflate(CloudVolume.HORIZONTAL_REACH, CloudVolume.VERTICAL_REACH, CloudVolume.HORIZONTAL_REACH);
    }

    public static int signal(ServerLevel level, BlockPos detector) {
        var clouds = in(level, new AABB(detector).inflate(DETECTOR_REACH));
        if (clouds.isEmpty()) return 0;
        var reachable = CloudVolume.reachable(level, detector, DETECTOR_REACH);
        int signal = 0;
        for (var cloud : clouds) {
            var pos = cloud.volume().nearest(detector, reachable);
            if (pos == null) continue;
            double distance = Math.sqrt(detector.distSqr(pos));
            signal = Math.max(signal, (int) Math.ceil(15 * cloud.charge() * (1 - distance / (DETECTOR_REACH + 1))));
        }
        return signal;
    }

    public static FluidStack collect(ServerLevel level, BlockPos intake, FluidStack stored, int limit) {
        if (limit <= 0) return FluidStack.EMPTY;
        var clouds = in(level, new AABB(intake).inflate(DETECTOR_REACH));
        if (clouds.isEmpty()) return FluidStack.EMPTY;
        var reachable = CloudVolume.reachable(level, intake, DETECTOR_REACH);
        GasCloud nearest = null;
        double closest = Double.MAX_VALUE;
        for (var cloud : clouds) {
            if (!stored.isEmpty() && !cloud.compatible(stored)) continue;
            var cell = cloud.volume().nearest(intake, reachable);
            if (cell == null) continue;
            double distance = intake.distSqr(cell);
            if (distance < closest) {
                nearest = cloud;
                closest = distance;
            }
        }
        return nearest == null ? FluidStack.EMPTY : nearest.extract(limit);
    }
}
