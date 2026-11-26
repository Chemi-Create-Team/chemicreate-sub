package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.LargeMechanicalPressBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class LargeMechanicalPressRenderer extends KineticBlockEntityRenderer<LargeMechanicalPressBlockEntity> {
    private static final PartialModel PLATEN = PartialModel.of(CreatePlant.location("block/large_press/platen"));
    private static final PartialModel RAM = PartialModel.of(CreatePlant.location("block/large_press/ram"));

    public static void init() { }

    public LargeMechanicalPressRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected void renderSafe(LargeMechanicalPressBlockEntity be, float partialTicks, PoseStack pose,
                                        MultiBufferSource buffers, int light, int overlay) {
        var solid = buffers.getBuffer(RenderType.solid());
        var state = be.getBlockState();
        renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.UP), pose, solid, light);
        float travel = be.pressing().getRenderedHeadOffset(partialTicks) * 1.0625f;
        CachedBuffers.partial(PLATEN, state).translate(0, -travel, 0).light(light).renderInto(pose, solid);
        pose.pushPose();
        pose.translate(0, .0625f - travel, 0);
        pose.scale(1, .25f + travel, 1);
        CachedBuffers.partial(RAM, state).light(light).renderInto(pose, solid);
        pose.popPose();
    }
}
