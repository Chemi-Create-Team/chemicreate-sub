package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createplant.CreatePlant;
import net.createplant.block.entity.HighPressurePumpBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class PressurePumpRenderer extends KineticBlockEntityRenderer<HighPressurePumpBlockEntity> {
    private static final PartialModel DRIVE = PartialModel.of(CreatePlant.location("block/pressure_pump_spindle"));
    public static void init() { }

    public PressurePumpRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected void renderSafe(HighPressurePumpBlockEntity pump, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (pump.drive()) {
            var rotating = CachedBuffers.partial(DRIVE, pump.getBlockState());
            standardKineticRotationTransform(rotating, pump, light).renderInto(pose, buffers.getBuffer(RenderType.solid()));
        }
        HoseRenderer.draw(pump, pose, buffers, light);
    }

    @Override public AABB getRenderBoundingBox(HighPressurePumpBlockEntity pump) {
        return HoseRenderer.bounds(pump).expandTowards(0, .35, 0);
    }

    @Override public boolean shouldRenderOffScreen(HighPressurePumpBlockEntity pump) { return true; }
    @Override public int getViewDistance() { return 96; }
}
