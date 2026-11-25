package net.createplant.arm;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Joint angles of a Create mechanical arm reaching a point. The angle maths is Create's {@code ArmAngleTarget}, whose angles are
 * package-private; they are read by reflection, because AGENTS.md rules out Mixin and a class in Create's package would split it.
 * Any failure gives the rest pose, so a pose can never crash the renderer.
 */
public final class ArmPose {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    /** base, lower arm, upper arm, head, as Create's arm block entity chases them. */
    public static final float[] REST = {0, 135, 45, 0};
    private static Field base, lower, upper, head;
    private static boolean broken;

    private ArmPose() {}

    private static synchronized boolean ready() {
        if (broken) return false;
        if (base != null) return true;
        try {
            Class<?> c = Class.forName("com.simibubi.create.content.kinetics.mechanicalArm.ArmAngleTarget");
            base = field(c, "baseAngle");
            lower = field(c, "lowerArmAngle");
            upper = field(c, "upperArmAngle");
            head = field(c, "headAngle");
            try {
                Object rest = field(c, "NO_TARGET").get(null);
                REST[0] = base.getFloat(rest);
                REST[1] = lower.getFloat(rest);
                REST[2] = upper.getFloat(rest);
                REST[3] = head.getFloat(rest);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // keep the standard rest pose
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LOGGER.warn("Cannot read the angles of Create's arm, the industrial arm will stay in its rest pose", e);
            return false;
        }
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    public static float[] toward(BlockPos arm, Vec3 target) {
        if (!ready()) return REST.clone();
        try {
            Object t = Class.forName("com.simibubi.create.content.kinetics.mechanicalArm.ArmAngleTarget")
                    .getConstructor(BlockPos.class, Vec3.class, Direction.class, boolean.class).newInstance(arm, target, Direction.DOWN, false);
            float[] a = {base.getFloat(t), lower.getFloat(t), upper.getFloat(t), head.getFloat(t)};
            for (float f : a) if (Float.isNaN(f) || Float.isInfinite(f)) return REST.clone();
            return a;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return REST.clone();
        }
    }
}
