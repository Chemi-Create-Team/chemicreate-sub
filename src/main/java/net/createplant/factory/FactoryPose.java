package net.createplant.factory;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity.ContraptionRotationState;
import com.simibubi.create.foundation.collision.Matrix3d;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** T(origin) × Ry(yaw) × Rx(pitch) × Rz(spin) × S(size). Rotation is shared with Create's OBB solver. */
public record FactoryPose(Vec3 center, float yaw, float pitch, float spin, float width, float height, float length) {
    public static final FactoryPose IDENTITY = new FactoryPose(Vec3.ZERO, 0, 0, 0, 1, 1, 1);

    public FactoryPose interpolate(FactoryPose end, float partial) {
        return new FactoryPose(center.lerp(end.center, partial), Mth.rotLerp(partial, yaw, end.yaw),
            Mth.rotLerp(partial, pitch, end.pitch), Mth.rotLerp(partial, spin, end.spin),
            Mth.lerp(partial, width, end.width), Mth.lerp(partial, height, end.height), Mth.lerp(partial, length, end.length));
    }

    public Quaternionf rotation() { return new Quaternionf().rotationYXZ(yaw * Mth.DEG_TO_RAD, pitch * Mth.DEG_TO_RAD, spin * Mth.DEG_TO_RAD); }

    public ContraptionRotationState collisionRotation() {
        var rotation = new ContraptionRotationState() {
            @Override public Matrix3d asMatrix() {
                return new Matrix3d().asZRotation(-spin * Mth.DEG_TO_RAD)
                    .multiply(new Matrix3d().asXRotation(-pitch * Mth.DEG_TO_RAD))
                    .multiply(new Matrix3d().asYRotation(-yaw * Mth.DEG_TO_RAD));
            }
        };
        rotation.xRotation = pitch;
        rotation.yRotation = yaw;
        rotation.zRotation = spin;
        return rotation;
    }

    public Vector3f sized() { return new Vector3f(width, height, length); }

    public static FactoryPose wire(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        return new FactoryPose(from.lerp(to, .5), (float) Math.toDegrees(Math.atan2(d.x, d.z)),
            (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())), 0, 1, 1, (float) d.length());
    }
}
