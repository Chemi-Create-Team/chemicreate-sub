package net.createplant.arm;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Joint angles of the industrial arm reaching a point. This is the maths of Create's {@code ArmAngleTarget} (claw facing down,
 * floor mounted), with the two arm segments {@link #SCALE} times longer.
 * {@code ArmAssemblyGameTests} checks it against Create's own result at scale 1.
 */
public final class ArmPose {
    /** base, lower arm, upper arm, head, as Create's arm block entity chases them. */
    public static final float[] REST = {0, 135, 45, 0};
    /** The two arm segments are this many times longer than Create's, so the arm reaches the depots and belts around it. */
    public static final float SCALE = 2;
    /** Create's shoulder above the arm block's centre, the lengths of its two arm segments and its shortest reach. */
    public static final double SHOULDER = .375, LOWER = .875, UPPER = .9375, NEAREST = .125;
    /** Create puts the wrist half a block above the point the claw aims at. */
    private static final double WRIST = .5;

    private ArmPose() {}

    private static Vec3 shoulder(BlockPos arm) {
        return Vec3.atCenterOf(arm).add(0, SHOULDER, 0);
    }

    /** Whether the claw of the arm at {@code arm} can touch {@code target}; the arm stops short of anything farther. */
    public static boolean reaches(BlockPos arm, Vec3 target) {
        double d = target.add(0, WRIST, 0).distanceTo(shoulder(arm));
        return d <= (LOWER + UPPER) * SCALE && d >= NEAREST;
    }

    /** How much higher the wrist can go while a telescoping tool still reaches the same point. */
    public static double wristClearance(BlockPos arm, Vec3 target, double desired) {
        Vec3 offset = target.add(0, WRIST, 0).subtract(shoulder(arm));
        double reach = (LOWER + UPPER) * SCALE;
        double upper = Math.sqrt(Math.max(0, reach * reach - offset.horizontalDistanceSqr()));
        return Math.clamp(upper - offset.y, 0, desired);
    }

    /** Joint angles that put the claw on {@code target}. */
    public static float[] toward(BlockPos arm, Vec3 target) {
        float[] r = angles(shoulder(arm), target, (float) (LOWER * SCALE), (float) (UPPER * SCALE));
        for (float f : r) if (!Float.isFinite(f)) return REST.clone();
        return r;
    }

    /** Create's ArmAngleTarget with segment lengths {@code a} and {@code b}. */
    public static float[] angles(Vec3 origin, Vec3 target, float a, float b) {
        Vec3 diff = target.add(0, WRIST, 0).subtract(origin);
        float h = (float) diff.multiply(1, 0, 1).length();
        float base = AngleHelper.deg(Mth.atan2(diff.x, diff.z)) + 180;
        float alpha = AngleHelper.deg(Mth.atan2(diff.y, h));
        float d = Mth.clamp(Mth.sqrt((float) (diff.y * diff.y + h * h)), (float) NEAREST, a + b), d2 = d * d;
        float lower = AngleHelper.deg(Math.acos((a * a + d2 - b * b) / (2 * a * d))) + alpha;
        float upper = AngleHelper.deg(Math.acos((a * a + b * b - d2) / (2 * b * a)));
        if (Float.isNaN(lower)) lower = 0;
        if (Float.isNaN(upper)) upper = 0;
        Vec3 wrist = VecHelper.rotate(new Vec3(0, b, 0), upper + 180, Direction.Axis.X);
        wrist = VecHelper.rotate(wrist.add(0, a, 0), lower - 90, Direction.Axis.X);
        wrist = VecHelper.rotate(wrist, base, Direction.Axis.Y).add(origin);
        Vec3 head = target.subtract(wrist);
        float head2 = AngleHelper.deg(Mth.atan2(head.y, head.multiply(1, 0, 1).length())) - lower - upper - 135;
        return new float[] {base, lower, upper, head2};
    }
}
