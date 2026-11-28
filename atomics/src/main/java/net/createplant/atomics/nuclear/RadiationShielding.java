package net.createplant.atomics.nuclear;

import net.createplant.CreatePlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Narrow-beam attenuation along the actual length of the ray inside each metre voxel. */
public final class RadiationShielding {
    public static final TagKey<net.minecraft.world.level.block.Block> WATER = tag("water"),
            CONCRETE = tag("concrete"),
            STEEL = tag("steel"),
            SERPENTINITE = tag("serpentinite");

    private static TagKey<net.minecraft.world.level.block.Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, CreatePlant.location("radiation_shielding/" + name));
    }

    public record Material(double gammaHvl, double neutronHvl, double betaHvl) {}

    public record Attenuation(double gamma, double neutron, double beta) {}

    private record Key(Vec3 start, Vec3 end) {}

    private record Cached(Attenuation value, Map<Long, Long> revisions) {}

    private final Map<Long, Long> revisions = new java.util.HashMap<>();
    private final LinkedHashMap<Key, Cached> cache = new LinkedHashMap<>(128, .75f, true);
    public long rays;

    public void changed(BlockPos pos) {
        revisions.merge(net.minecraft.world.level.ChunkPos.asLong(pos), 1L, Long::sum);
    }

    public void clear() {
        revisions.clear();
        cache.clear();
    }

    public static Material material(BlockState state) {
        if (state.is(WATER) || state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))
            return new Material(.09, .1, .003);
        if (state.is(SERPENTINITE)) return new Material(.04, .09, .001);
        if (state.is(CONCRETE)) return new Material(.05, .12, .001);
        if (state.is(STEEL)) return new Material(.016, .3, .0005);
        if (state.isAir() || !state.getFluidState().isEmpty())
            return new Material(
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        if (state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.PLANKS)) return new Material(.4, .2, .002);
        return new Material(.1, .2, .001);
    }

    public Attenuation trace(ServerLevel level, Vec3 from, Vec3 to) {
        var key = new Key(from, to);
        var old = cache.get(key);
        if (old != null
                && old.revisions.entrySet().stream()
                        .allMatch(e -> revisions.getOrDefault(e.getKey(), 0L).equals(e.getValue())))
            return old.value;
        rays++;
        double distance = from.distanceTo(to), t = 0, gamma = 0, neutron = 0, beta = 0;
        var used = new java.util.HashMap<Long, Long>();
        Vec3 delta = to.subtract(from);
        var cursor = new BlockPos.MutableBlockPos();
        for (int steps = 0; steps < 256 && t < 1 - 1e-12; steps++) {
            var at = from.add(delta.scale(Math.min(1, t + 1e-9)));
            cursor.set(BlockPos.containing(at));
            long chunk = net.minecraft.world.level.ChunkPos.asLong(cursor);
            used.put(chunk, revisions.getOrDefault(chunk, 0L));
            if (!level.hasChunkAt(cursor)) {
                gamma = neutron = beta = 1024;
                break;
            }
            double next = 1;
            for (int axis = 0; axis < 3; axis++) {
                double start = axis == 0 ? from.x : axis == 1 ? from.y : from.z,
                        d = axis == 0 ? delta.x : axis == 1 ? delta.y : delta.z;
                int block = axis == 0 ? cursor.getX() : axis == 1 ? cursor.getY() : cursor.getZ();
                if (Math.abs(d) > 1e-12)
                    next = Math.min(next, ((block + (d > 0 ? 1 : 0)) - start) / d);
            }
            if (next <= t) next = Math.min(1, t + 1e-7);
            double length = (next - t) * distance;
            var state = level.getBlockState(cursor);
            var material = material(state);
            if (state.hasBlockEntity() && !state.is(SERPENTINITE) && !state.is(CONCRETE))
                length = Math.min(length, .01);
            else if (state.getFluidState().isEmpty()) {
                double solid = 0;
                for (var box : state.getCollisionShape(level, cursor).toAabbs())
                    solid += lengthInside(box.move(cursor), from, to);
                length = Math.min(length, solid);
            }
            if (!state.isAir()) {
                gamma += length / material.gammaHvl;
                neutron += length / material.neutronHvl;
                beta += length / material.betaHvl;
            }
            t = next;
        }
        var result =
                new Attenuation(Math.pow(2, -gamma), Math.pow(2, -neutron), Math.pow(2, -beta));
        cache.put(key, new Cached(result, used));
        if (cache.size() > RadiationPhysics.MAX_CACHE)
            cache.remove(cache.keySet().iterator().next());
        return result;
    }

    public static double lengthInside(AABB box, Vec3 from, Vec3 to) {
        double min = 0, max = 1;
        var d = to.subtract(from);
        for (int axis = 0; axis < 3; axis++) {
            double origin = axis == 0 ? from.x : axis == 1 ? from.y : from.z,
                    delta = axis == 0 ? d.x : axis == 1 ? d.y : d.z;
            double low = axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ,
                    high = axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ;
            if (Math.abs(delta) < 1e-12) {
                if (origin < low || origin > high) return 0;
                continue;
            }
            double a = (low - origin) / delta, b = (high - origin) / delta;
            min = Math.max(min, Math.min(a, b));
            max = Math.min(max, Math.max(a, b));
            if (max <= min) return 0;
        }
        return (max - min) * from.distanceTo(to);
    }
}
