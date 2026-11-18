package net.createplant.block;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HighPressureTurbineShapes {
    private static final double[][] CASING_CORNER_POSITIVE_POSITIVE = {
        {0.746282, -0.034256},
        {0.951541, 0.171004},
        {0.190640, 0.931905},
        {-0.014620, 0.726646}
    };
    private static final double[][] CASING_CORNER_NEGATIVE_POSITIVE = {
        {1.014620, 0.726646},
        {0.809360, 0.931905},
        {0.048459, 0.171004},
        {0.253718, -0.034256}
    };
    private static final double[][] CASING_CORNER_NEGATIVE_NEGATIVE = {
        {0.271912, 1.016328},
        {0.066653, 0.811068},
        {0.827554, 0.050167},
        {1.032814, 0.255426}
    };
    private static final double[][] CASING_CORNER_POSITIVE_NEGATIVE = {
        {-0.032814, 0.255426},
        {0.172446, 0.050167},
        {0.933347, 0.811068},
        {0.728088, 1.016328}
    };

    private static final Map<CornerKey, VoxelShape> CASING_CORNER_CACHE = new ConcurrentHashMap<>();
    private static final Map<CornerKey, VoxelShape> LOW_PRESSURE_CASING_CORNER_CACHE = new ConcurrentHashMap<>();
    private static final Map<CornerKey, VoxelShape> END_CORNER_CACHE = new ConcurrentHashMap<>();

    private HighPressureTurbineShapes() {
    }

    /**
     * Shape helper overload.
     */
    static VoxelShape casingCornerPanel(Direction.Axis axis, int ringX, int ringY) {
        return casingCornerPanel(axis, ringX, ringY, 4);
    }

    /**
     * Shape helper overload.
     */
    static VoxelShape casingCornerPanel(Direction.Axis axis, int ringX, int ringY, int slices) {
        int safeSlices = Math.max(1, slices);
        CornerKey key = new CornerKey(axis, ringX, ringY, safeSlices);

        return CASING_CORNER_CACHE.computeIfAbsent(
            key,
            k -> buildCasingCornerPanel(k.axis(), k.ringX(), k.ringY(), k.slices())
        );
    }

    static VoxelShape lowPressureCasingCornerPanel(Direction.Axis axis, int ringX, int ringY, int slices) {
        int safeSlices = Math.max(1, slices);
        CornerKey key = new CornerKey(axis, ringX, ringY, safeSlices);
        return LOW_PRESSURE_CASING_CORNER_CACHE.computeIfAbsent(
            key,
            k -> buildLowPressureCasingCornerPanel(k.axis(), k.ringX(), k.ringY(), k.slices())
        );
    }

    private static VoxelShape buildLowPressureCasingCornerPanel(Direction.Axis axis, int ringX, int ringY,
        int slices) {
        VoxelShape shape = Shapes.empty();
        double stepSize = 1.0 / slices;

        for (int step = 0; step < slices; step++) {
            double minT = step * stepSize;
            double maxT = minT + stepSize;
            double[] pointA = lowPressureCornerPoint(ringX, ringY, minT, 0);
            double[] pointB = lowPressureCornerPoint(ringX, ringY, minT, 1);
            double[] pointC = lowPressureCornerPoint(ringX, ringY, maxT, 0);
            double[] pointD = lowPressureCornerPoint(ringX, ringY, maxT, 1);
            shape = orUnoptimized(shape, localBox(
                axis,
                0.0, 16.0,
                min(pointA[0], pointB[0], pointC[0], pointD[0]) * 16.0,
                max(pointA[0], pointB[0], pointC[0], pointD[0]) * 16.0,
                min(pointA[1], pointB[1], pointC[1], pointD[1]) * 16.0,
                max(pointA[1], pointB[1], pointC[1], pointD[1]) * 16.0
            ));
        }

        return shape.optimize();
    }

    private static double[] lowPressureCornerPoint(int ringX, int ringY, double t, int edge) {
        double[] point = casingCornerPoint(ringX, ringY, t, edge);
        return new double[] {
            scaledCornerCoordinate(point[0], ringX),
            scaledCornerCoordinate(point[1], ringY)
        };
    }

    private static double scaledCornerCoordinate(double localCoordinate, int sign) {
        double sourceBlockOrigin = sign > 0 ? 1.0 : -1.0;
        double targetBlockOrigin = sign > 0 ? 2.0 : -2.0;
        double sourceWorldCoordinate = sourceBlockOrigin + localCoordinate;
        double scaledWorldCoordinate = 0.5 + (sourceWorldCoordinate - 0.5) * (5.0 / 3.0);
        return scaledWorldCoordinate - targetBlockOrigin;
    }

    private static VoxelShape buildCasingCornerPanel(Direction.Axis axis, int ringX, int ringY, int slices) {
        VoxelShape shape = Shapes.empty();

        double stepSize = 1.0 / slices;

        for (int step = 0; step < slices; step++) {
            double minT = step * stepSize;
            double maxT = minT + stepSize;
            double[] pointA = casingCornerPoint(ringX, ringY, minT, 0);
            double[] pointB = casingCornerPoint(ringX, ringY, minT, 1);
            double[] pointC = casingCornerPoint(ringX, ringY, maxT, 0);
            double[] pointD = casingCornerPoint(ringX, ringY, maxT, 1);
            double ringXMin = min(pointA[0], pointB[0], pointC[0], pointD[0]) * 16.0;
            double ringXMax = max(pointA[0], pointB[0], pointC[0], pointD[0]) * 16.0;
            double ringYMin = min(pointA[1], pointB[1], pointC[1], pointD[1]) * 16.0;
            double ringYMax = max(pointA[1], pointB[1], pointC[1], pointD[1]) * 16.0;

            shape = orUnoptimized(
                shape,
                localBox(
                    axis,
                    0.0, 16.0,
                    ringXMin,
                    ringXMax,
                    ringYMin,
                    ringYMax
                )
            );
        }

        return shape.optimize();
    }

    private static double[] casingCornerPoint(int ringX, int ringY, double t, int edge) {
        double[][] points = casingCornerPoints(ringX, ringY);
        double[] start = points[edge];
        double[] end = points[3 - edge];
        double x = Mth.lerp(t, start[0], end[0]);
        double y = Mth.lerp(t, start[1], end[1]);
        return new double[] {x, y};
    }

    public static double[][] casingCornerPoints(int ringX, int ringY) {
        if (ringX > 0 && ringY > 0) {
            return CASING_CORNER_POSITIVE_POSITIVE;
        }
        if (ringX < 0 && ringY > 0) {
            return CASING_CORNER_NEGATIVE_POSITIVE;
        }
        if (ringX < 0 && ringY < 0) {
            return CASING_CORNER_NEGATIVE_NEGATIVE;
        }
        return CASING_CORNER_POSITIVE_NEGATIVE;
    }

    /**
     * Shape helper overload.
     */
    static VoxelShape endCorner(Direction.Axis axis, int ringX, int ringY) {
        return endCorner(axis, ringX, ringY, 4);
    }

    /**
     * Shape helper overload.
     */
    static VoxelShape endCorner(Direction.Axis axis, int ringX, int ringY, int slices) {
        int safeSlices = Math.max(1, slices);
        CornerKey key = new CornerKey(axis, ringX, ringY, safeSlices);

        return END_CORNER_CACHE.computeIfAbsent(
            key,
            k -> buildEndCorner(k.axis(), k.ringX(), k.ringY(), k.slices())
        );
    }

    private static VoxelShape buildEndCorner(Direction.Axis axis, int ringX, int ringY, int slices) {
        VoxelShape shape = Shapes.empty();

        double stepSize = 16.0 / slices;

        for (int step = 0; step < slices; step++) {
            double xMin = step * stepSize;
            double xMax = xMin + stepSize;

            double yMax = 16.0 - step * stepSize;

            shape = orUnoptimized(
                shape,
                localBox(
                    axis,
                    0.0, 16.0,
                    centerMin(ringX, xMin, xMax),
                    centerMax(ringX, xMin, xMax),
                    centerMin(ringY, 0.0, yMax),
                    centerMax(ringY, 0.0, yMax)
                )
            );
        }

        return shape.optimize();
    }

    private static VoxelShape orUnoptimized(VoxelShape shape, VoxelShape other) {
        return Shapes.joinUnoptimized(shape, other, BooleanOp.OR);
    }

    private static double centerMin(int sign, double min, double max) {
        return sign > 0 ? min : 16.0 - max;
    }

    private static double centerMax(int sign, double min, double max) {
        return sign > 0 ? max : 16.0 - min;
    }

    private static double min(double a, double b, double c, double d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static double max(double a, double b, double c, double d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    static VoxelShape localBox(
        Direction.Axis axis,
        double axialMin,
        double axialMax,
        double ringXMin,
        double ringXMax,
        double ringYMin,
        double ringYMax
    ) {
        return switch (axis) {
            case X -> Block.box(axialMin, ringYMin, ringXMin, axialMax, ringYMax, ringXMax);
            case Y -> Block.box(ringXMin, axialMin, ringYMin, ringXMax, axialMax, ringYMax);
            case Z -> Block.box(ringXMin, ringYMin, axialMin, ringXMax, ringYMax, axialMax);
        };
    }

    private record CornerKey(Direction.Axis axis, int ringX, int ringY, int slices) {
    }
}
