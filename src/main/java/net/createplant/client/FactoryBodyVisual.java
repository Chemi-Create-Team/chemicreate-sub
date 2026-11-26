package net.createplant.client;

import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.createplant.contraptions.FactoryBodyEntity;
import net.createplant.factory.FactoryGeometry;
import net.createplant.factory.FactoryPose;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public final class FactoryBodyVisual extends AbstractEntityVisual<FactoryBodyEntity> implements SimpleDynamicVisual {
    private TransformedInstance instance;
    private FactoryBodyMesh mesh;
    private FactoryPose shown;
    private Vec3i lastOrigin;
    private int lastLight = -1;

    public FactoryBodyVisual(VisualizationContext context, FactoryBodyEntity entity, float partial) { super(context, entity, partial); }

    @Override public void beginFrame(DynamicVisual.Context frame) {
        if (!entity.hasPose() || entity.geometry() == null) return;
        var pose = entity.pose(frame.partialTick());
        var origin = renderOrigin();
        int light = entity.geometry() == FactoryGeometry.SLAB || entity.geometry() == FactoryGeometry.BUCKLED_SLAB ? LightTexture.FULL_BRIGHT
            : LevelRenderer.getLightColor(level, BlockPos.containing(pose.center()));
        var needed = FactoryBodyMesh.get(entity.geometry(), pose.length());
        if (needed != mesh) {
            if (instance != null) instance.delete();
            mesh = needed;
            instance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, mesh.model()).createInstance();
            shown = null;
        }
        if (pose.equals(shown) && light == lastLight && origin.equals(lastOrigin)) return;
        shown = pose;
        lastOrigin = origin;
        lastLight = light;
        instance.setIdentityTransform().translate((float) (pose.center().x - origin.getX()), (float) (pose.center().y - origin.getY()),
            (float) (pose.center().z - origin.getZ())).rotate(pose.rotation()).scale(pose.width(), pose.height(), pose.length()).light(light).setChanged();
    }

    @Override protected void _delete() { if (instance != null) instance.delete(); }
}
