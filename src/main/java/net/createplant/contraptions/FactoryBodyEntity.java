package net.createplant.contraptions;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.collision.CollisionList;
import com.simibubi.create.foundation.collision.ContinuousOBBCollider;
import com.simibubi.create.foundation.collision.OrientedBB;
import net.createplant.block.entity.ContinuousFactoryBlockEntity;
import net.createplant.factory.FactoryGeometry;
import net.createplant.factory.FactoryPose;
import net.createplant.registry.CEContinuousFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Manufactured parts are recreated from the controller. They never disassemble into or claim world blocks. */
public final class FactoryBodyEntity extends AbstractContraptionEntity {
    private static final EntityDataAccessor<Vector3f> POSITION = SynchedEntityData.defineId(FactoryBodyEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> ANGLES = SynchedEntityData.defineId(FactoryBodyEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> SIZE = SynchedEntityData.defineId(FactoryBodyEntity.class, EntityDataSerializers.VECTOR3);
    private FactoryPose previous, current;
    private ContraptionRotationState collisionRotation = ContraptionRotationState.NONE;

    public FactoryBodyEntity(EntityType<?> type, Level level) { super(type, level); }

    public static FactoryBodyEntity create(Level level, BlockPos owner, FactoryGeometry geometry, FactoryPose pose) {
        var body = new FactoryBodyEntity(CEContinuousFactory.BODY_ENTITY.get(), level);
        body.setContraption(new FactoryContraption(owner, geometry));
        body.target(pose);
        body.updatePose();
        return body;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POSITION, new Vector3f()).define(ANGLES, new Vector3f()).define(SIZE, new Vector3f(1));
    }

    public FactoryGeometry geometry() { return contraption instanceof FactoryContraption factory ? factory.geometry() : null; }
    public boolean hasPose() { return current != null; }
    public FactoryPose pose(float partial) { return current == null ? FactoryPose.IDENTITY : previous.interpolate(current, partial); }

    public void target(FactoryPose pose) {
        var offset = pose.center().subtract(((FactoryContraption) contraption).owner().getCenter());
        entityData.set(POSITION, offset.toVector3f());
        entityData.set(ANGLES, new Vector3f(Mth.wrapDegrees(pose.yaw()), Mth.wrapDegrees(pose.pitch()), Mth.wrapDegrees(pose.spin())));
        entityData.set(SIZE, pose.sized());
    }

    private void updatePose() {
        var factory = (FactoryContraption) contraption;
        Vector3f offset = entityData.get(POSITION), angles = entityData.get(ANGLES), size = entityData.get(SIZE);
        Vec3 center = factory.owner().getCenter().add(offset.x(), offset.y(), offset.z());
        var next = new FactoryPose(center, angles.x(), angles.y(), angles.z(), size.x(), size.y(), size.z());
        previous = current == null ? next : current;
        if (next.equals(current)) return;
        current = next;
        collisionRotation = next.collisionRotation();
        factory.resize(size.x(), size.y(), size.z());
        setPos(center.x - .5, center.y - .5, center.z - .5);
    }

    @Override public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (current != null && contraption != null) setBoundingBox(worldBounds(contraption.bounds, current.center()));
    }

    private AABB worldBounds(AABB local, Vec3 center) {
        var mat = collisionRotation.asMatrix();
        Vec3 low = mat.transformTransposed(new Vec3(local.minX - .5, local.minY - .5, local.minZ - .5)).add(center);
        var bounds = new AABB(low, low);
        for (int i = 1; i < 8; i++) {
            Vec3 corner = mat.transformTransposed(new Vec3(((i & 1) == 0 ? local.minX : local.maxX) - .5,
                ((i & 2) == 0 ? local.minY : local.maxY) - .5, ((i & 4) == 0 ? local.minZ : local.maxZ) - .5)).add(center);
            bounds = bounds.minmax(new AABB(corner, corner));
        }
        return bounds;
    }

    @Override protected void tickContraption() {
        var owner = ((FactoryContraption) contraption).owner();
        if (!level().isClientSide && (!level().hasChunkAt(owner) || !(level().getBlockEntity(owner) instanceof ContinuousFactoryBlockEntity machine)
            || !machine.owns(this))) { discard(); return; }
        updatePose();
        if (!level().isClientSide && (tickCount + getId()) % 4 == 0 && previous != current) checkPinch();
    }

    private void checkPinch() {
        if (geometry() == FactoryGeometry.CHARGE || geometry() == FactoryGeometry.WIRE) return;
        if (Math.abs(Mth.wrapDegrees(current.spin() - previous.spin())) < 1.8 && current.center().distanceToSqr(previous.center()) < .0025) return;
        var worldToLocal = collisionRotation.asMatrix();
        var nearby = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(.04), e -> !e.isSpectator() && e.isAlive());
        if (nearby.isEmpty()) return;
        var scratch = new CollisionList();
        for (var living : nearby) {
            var box = living.getBoundingBox().inflate(.04);
            var body = new OrientedBB(worldToLocal.transform(box.getCenter().subtract(current.center())).add(.5, .5, .5),
                new Vec3(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2), worldToLocal);
            var response = ContinuousOBBCollider.collideMany(contraption.getSimplifiedEntityColliders(), scratch, body, Vec3.ZERO, 0, false);
            if (response.surfaceCollision || response.collisionResponse.lengthSqr() > 1e-9) living.hurt(damageSources().cramming(), 3);
        }
    }

    @Override public boolean collisionEnabled() { return current != null && super.collisionEnabled(); }
    @Override public boolean supportsTerrainCollision() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public void disassemble() { discard(); }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) { }
    @Override public ContraptionRotationState getRotationState() { return collisionRotation; }
    @Override public Vec3 applyRotation(Vec3 vector, float partial) { return pose(partial).collisionRotation().asMatrix().transformTransposed(vector); }
    @Override public Vec3 reverseRotation(Vec3 vector, float partial) { return pose(partial).collisionRotation().asMatrix().transform(vector); }
    @Override public void applyLocalTransforms(PoseStack stack, float partial) {
        stack.translate(.5, .5, .5);
        stack.mulPose(pose(partial).rotation());
        stack.translate(-.5, -.5, -.5);
    }
    @Override protected StructureTransform makeStructureTransform() { return new StructureTransform(BlockPos.containing(position()), 0, 0, 0); }
    @Override protected float getStalledAngle() { return 0; }
    @Override protected void handleStallInformation(double x, double y, double z, float angle) { }
}
