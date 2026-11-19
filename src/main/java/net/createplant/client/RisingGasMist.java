package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.createplant.cloud.CloudVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class RisingGasMist {
    static final int SKY_RISE = 7;
    static final float SKY_SPREAD = 2.1f;
    private static final int OPEN_LANES = 4, COVERED_LANES = 12;
    private final BlockPos origin;
    private final List<Column> columns;
    private List<Lane> lanes = List.of();
    private float nextCheck;

    private record Column(BlockPos base, int top) { }
    private record Lane(float x, float z, float bottom, float top, float wideAbove, float phase, boolean open) { }

    RisingGasMist(CloudVolume volume) {
        origin = volume.origin();
        var found = new ArrayList<Column>();
        for (var pos : volume.positions()) {
            if (volume.contains(pos.below())) continue;
            int top = pos.getY() + 1;
            var cursor = pos.mutable();
            while (volume.contains(cursor.setY(top))) top++;
            found.add(new Column(pos, top));
        }
        found.sort(Comparator.comparingDouble(column -> column.base().distSqr(origin)));
        columns = List.copyOf(found);
    }

    void render(Level level, boolean preview, Quaternionf camera, VertexConsumer buffer, PoseStack.Pose pose,
        float time, float charge, float distanceFade, int color) {
        if (time >= nextCheck) {
            updateLanes(level, preview);
            nextCheck = time + 20;
        }
        var right = new Vector3f(1, 0, 0);
        var up = new Vector3f(0, 1, 0);
        if (preview) {
            var view = pose.pose();
            right.set(view.m00(), view.m10(), view.m20()).normalize();
            up.set(view.m01(), view.m11(), view.m21()).normalize();
        } else {
            right.rotate(camera);
            up.rotate(camera);
        }
        for (var lane : lanes) {
            int count = lane.open() ? 8 : 2;
            for (int i = 0; i < count; i++) {
                float spacing = (i + lane.phase() + .2f * Mth.sin(i * 2.3f)) / count;
                float clock = time / (lane.open() ? 68f : 42f) - spacing;
                if (clock < 0) continue;
                float age = clock - Mth.floor(clock);
                drawPuff(buffer, pose, lane, right, up, age, i, charge, distanceFade, color);
            }
        }
    }

    private void updateLanes(Level level, boolean preview) {
        var selected = new ArrayList<Lane>(OPEN_LANES + COVERED_LANES);
        int openCount = 0, coveredCount = 0, lowestOpenY = Integer.MAX_VALUE;
        for (var column : columns) {
            var base = column.base();
            boolean open = !preview && roofHeight(level, base.getX(), base.getZ()) <= column.top();
            if (open) {
                if (openCount >= OPEN_LANES || openCount > 0 && base.getY() > lowestOpenY + 1) continue;
                openCount++;
                lowestOpenY = Math.min(lowestOpenY, base.getY());
            } else {
                if (coveredCount >= COVERED_LANES) continue;
                coveredCount++;
            }
            int wideAbove = open ? surroundingRoof(level, base) : column.top();
            float phase = (HashCommon.murmurHash3(base.asLong()) >>> 48) / 65536f;
            selected.add(new Lane(base.getX() - origin.getX(), base.getZ() - origin.getZ(), base.getY() - origin.getY() - .5f,
                column.top() - origin.getY() - .5f, wideAbove - origin.getY() - .5f, phase, open));
            if (openCount == OPEN_LANES && coveredCount == COVERED_LANES) break;
        }
        lanes = selected;
    }

    private static int surroundingRoof(Level level, BlockPos base) {
        int height = level.getMinBuildHeight();
        for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                height = Math.max(height, roofHeight(level, base.getX() + x, base.getZ() + z));
            }
        }
        return height;
    }

    private static int roofHeight(Level level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return level.getMaxBuildHeight();
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void drawPuff(VertexConsumer buffer, PoseStack.Pose pose, Lane lane, Vector3f right, Vector3f up,
        float age, int index, float charge, float distanceFade, int color) {
        float fadeIn = Mth.clamp(age * 11, 0, 1);
        float left = 1 - age;
        float emission = lane.open() ? Mth.lerp(age, Mth.clamp(charge * 2 - .16f, 0, 1), 1) : 1;
        int alpha = (int) (235 * Mth.sqrt(charge) * distanceFade * fadeIn * left * (.71f + .29f * left) * emission);
        if (alpha == 0) return;

        float end = lane.top() + (lane.open() ? SKY_RISE - .45f : -.16f);
        float y = Mth.lerp(age * (.74f + .26f * age), lane.bottom() + .19f, end);
        float room = lane.open() ? Mth.clamp((y - lane.wideAbove() - .28f) / 1.35f, 0, 1) : 0;
        float phase = lane.phase() * Mth.TWO_PI + index * 1.83f;
        float drift = .07f + room * (.16f + .37f * age);
        float x = lane.x() + Mth.sin(phase + age * 2.8f) * drift;
        float z = lane.z() + Mth.cos(phase + age * 2.1f) * drift;
        // Bound the billboard's corners even when the camera is looking down from above.
        float radius = Math.min(Mth.lerp(room, .465f, SKY_SPREAD) - drift, .25f + age * 3.2f);
        radius = Math.min(radius, y - lane.bottom());
        if (lane.open()) radius = Math.min(radius, Math.min(Math.max(.39f, y - lane.wideAbove()), lane.top() + SKY_RISE - y));
        else radius = Math.min(radius, lane.top() - y);
        float size = radius * .7071f;
        float roll = .7f * Mth.sin(phase + age * 2);
        float cos = size * Mth.cos(roll), sin = size * Mth.sin(roll);
        float ax = right.x * cos + up.x * sin, ay = right.y * cos + up.y * sin, az = right.z * cos + up.z * sin;
        float bx = up.x * cos - right.x * sin, by = up.y * cos - right.y * sin, bz = up.z * cos - right.z * sin;
        GasCloudRenderer.vertex(buffer, pose, x - ax - bx, y - ay - by, z - az - bz, 0, 1, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x + ax - bx, y + ay - by, z + az - bz, 1, 1, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x + ax + bx, y + ay + by, z + az + bz, 1, 0, color, alpha);
        GasCloudRenderer.vertex(buffer, pose, x - ax + bx, y - ay + by, z - az + bz, 0, 0, color, alpha);
    }
}
