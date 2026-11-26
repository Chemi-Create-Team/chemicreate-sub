package net.createplant.factory;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FactoryMotion {
    public interface Parts { void put(int id, FactoryGeometry geometry, FactoryPose pose); }
    private static final double MILL_Y = .97;
    private static final float KILN_TILT = 2.7f;

    private FactoryMotion() { }

    public static void parts(FactoryKind kind, FactoryLayout layout, float angle, float[] stageAngles, List<FactoryBatch> batches, Parts put) {
        switch (kind) {
            case KILN -> kiln(layout, angle, batches, put);
            case MILL -> mill(layout, stageAngles, batches, put);
            case WINDER -> winder(layout, angle, batches, put);
        }
    }

    private static void kiln(FactoryLayout line, float angle, List<FactoryBatch> batches, Parts put) {
        double length = line.length() - 1;
        double center = (line.length() + 1) / 2d;
        double mid = line.length() / 2d;
        put.put(0, FactoryGeometry.DRUM, new FactoryPose(line.point(0, kilnHeight(center, center), center), line.yaw(),
            KILN_TILT, angle, 1, 1, (float) length));
        put.put(1, FactoryGeometry.GIRTH_GEAR, new FactoryPose(line.point(0, kilnHeight(center, mid), mid), line.yaw(), KILN_TILT, angle, 1, 1, 1));
        put.put(2, FactoryGeometry.PINION, new FactoryPose(line.point(0, kilnHeight(center, mid) - 2.60, mid), line.yaw(),
            KILN_TILT, 8 - angle * 3, 1, 1, 1));
        for (int end = 0; end < 2; end++) {
            double z = end == 0 ? 1.7 : line.length() - 1.7;
            double bearingY = kilnHeight(center, z) - 1.94;
            for (int side = 0; side < 2; side++) put.put(3 + end * 2 + side, FactoryGeometry.ROLLER,
                new FactoryPose(line.point(side == 0 ? -.73 : .73, bearingY, z), line.yaw(), KILN_TILT,
                    -angle * 3, 1, 1, .28f));
        }
        for (var batch : batches) {
            if (!batch.finished.isEmpty()) continue;
            double z = 1 + Math.min(batch.travel, length - .32);
            float tumble = angle * 2 + batch.slot * 40;
            double slide = Math.sin(tumble * Mth.DEG_TO_RAD) * .53;
            double y = kilnHeight(center, z) - 1.13 + Math.abs(slide) * .47;
            put.put(16 + batch.slot, FactoryGeometry.CHARGE, new FactoryPose(line.point(slide, y, z), line.yaw(), KILN_TILT, tumble, 1, 1, 1));
        }
    }

    private static double kilnHeight(double center, double z) { return 3.25 - Math.tan(KILN_TILT * Mth.DEG_TO_RAD) * (z - center); }

    private static void mill(FactoryLayout line, float[] angles, List<FactoryBatch> batches, Parts put) {
        FactoryBatch nearest = batches.isEmpty() ? null : batches.getFirst();
        float stockThickness = nearest == null ? .58f : nearest.thickness;
        for (int i = 0; i < line.drives().size(); i++) {
            double z = 2 + i * FactoryLayout.MILL_SPACING;
            for (var batch : batches) if (Math.abs(z - batch.travel) < 1.2) { stockThickness = batch.thickness; break; }
            double radius = .51, gap = FactoryProcess.slabThickness(stockThickness, i + 1);
            float yaw = line.yaw() + 90;
            put.put(i * 2, FactoryGeometry.ROLLER, new FactoryPose(line.point(0, MILL_Y + radius + gap / 2, z), yaw, 0, angles[i], 1, 1, 1));
            put.put(i * 2 + 1, FactoryGeometry.ROLLER, new FactoryPose(line.point(0, MILL_Y - radius - gap / 2, z), yaw, 0, -angles[i], 1, 1, 1));
            put.put(10 + i, FactoryGeometry.PINION, new FactoryPose(line.point(2.64, 1.12, z), yaw, 0, -angles[i], .68f, .68f, .44f));
        }
        for (var batch : batches) {
            if (!batch.finished.isEmpty()) continue;
            double passes = 0;
            for (int i = 0; i < line.drives().size(); i++) passes += Math.clamp((batch.travel - (1.65 + i * FactoryLayout.MILL_SPACING)) / .70, 0, 1);
            float height = FactoryProcess.slabThickness(batch.thickness, passes);
            put.put(16 + batch.slot, batch.damaged ? FactoryGeometry.BUCKLED_SLAB : FactoryGeometry.SLAB, new FactoryPose(line.point(0, MILL_Y, batch.travel),
                line.yaw(), 0, 0, 1, height, FactoryProcess.slabLength(batch.thickness, height)));
        }
    }

    private static void winder(FactoryLayout line, float angle, List<FactoryBatch> batches, Parts put) {
        double progress = batches.isEmpty() ? 0 : batches.getFirst().travel / line.length();
        float radius = (float) FactoryProcess.coilingRadius(progress);
        Vec3 takeup = line.point(0, 1.45, 4.15);
        put.put(0, FactoryGeometry.COIL, new FactoryPose(takeup, line.yaw(), 0, -angle, radius / .59f, radius / .59f, 1));
        put.put(9, FactoryGeometry.CARRIER, new FactoryPose(line.point(0, 1.45, 1.49), line.yaw(), 0, angle, 1, 1, 1));
        for (int i = 0; i < 4; i++) {
            float orbit = i * 90 + angle;
            double theta = orbit * Mth.DEG_TO_RAD;
            double x = Math.cos(theta), y = Math.sin(theta);
            Vec3 bobbin = line.point(x * 1.03, 1.45 + y * 1.03, 2);
            put.put(1 + i, FactoryGeometry.SPOOL, new FactoryPose(bobbin, line.yaw(), 0, orbit - angle * 2, 1, 1, 1));
            if (!batches.isEmpty() && batches.getFirst().finished.isEmpty() && !batches.getFirst().damaged) {
                Vec3 outlet = line.point(x * .81, 1.45 + y * .81, 2.36);
                Vec3 guide = line.point(x * radius * .70, 1.45 + y * radius * .70, 3.60 + progress * .85);
                put.put(5 + i, FactoryGeometry.WIRE, FactoryPose.wire(outlet, guide));
            }
        }
    }
}
