package net.createplant.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Clips a gameplay ray before it can query unloaded terrain. */
public final class LoadedRay {
    private LoadedRay() { }

    public static Vec3 end(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 previous = start;
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 4));
        for (int step = 1; step <= steps; step++) {
            var point = start.lerp(end, (double) step / steps);
            if (!level.hasChunkAt(BlockPos.containing(point))) return previous;
            previous = point;
        }
        return end;
    }
}
