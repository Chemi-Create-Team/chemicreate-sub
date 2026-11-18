package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrodeHeadBlock;
import net.createplant.block.entity.ElectrodeHeadBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * The crosshead with the rack of plates at the height the head has reached: in the head when raised, in the cell when lowered.
 */
public class ElectrodeHeadRenderer extends SafeBlockEntityRenderer<ElectrodeHeadBlockEntity> {
    private static final java.util.List<PartialModel> PLATES = java.util.stream.IntStream.rangeClosed(1, 16)
        .mapToObj(count -> PartialModel.of(CreatePlant.location("block/electrolysis_cell/plates_" + count))).toList();
    private static final PartialModel CROSSHEAD = PartialModel.of(CreatePlant.location("block/electrolysis_cell/crosshead"));
    /** The crosshead's model is drawn half a block low to keep its rod within the model bounds. */
    private static final float CROSSHEAD_RISE = .5f;
    public static void init() { }
    public ElectrodeHeadRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(ElectrodeHeadBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = be.getBlockState();var solid = buffers.getBuffer(RenderType.solid());
        pose.pushPose();
        // Raised, the crosshead's rod stands ten pixels above the lid; lowered, the plates reach a whole block down into the cell and the rod
        // still stands four pixels above it.
        pose.translate(0, -(10 + 6 * be.position(partialTicks)) / 16, 0);
        if (state.getValue(ElectrodeHeadBlock.AXIS) == Direction.Axis.X) { pose.translate(.5, 0, .5);pose.mulPose(Axis.YP.rotationDegrees(90));pose.translate(-.5, 0, -.5); }
        CachedBuffers.partial(CROSSHEAD, state).translate(0, CROSSHEAD_RISE, 0).light(light).renderInto(pose, solid);
        int plates = be.plates();
        if (plates > 0) CachedBuffers.partial(PLATES.get(Math.min(16, plates) - 1), state).light(light).renderInto(pose, solid);
        pose.popPose();
    }

    /** The plates reach a block down into the cell, and the crosshead's rod stands above the lid. */
    @Override public AABB getRenderBoundingBox(ElectrodeHeadBlockEntity be) { return new AABB(be.getBlockPos()).expandTowards(0, -1, 0).expandTowards(0, 1, 0); }
}
