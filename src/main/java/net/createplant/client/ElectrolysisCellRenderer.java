package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.CreatePlant;
import net.createplant.block.ElectrolysisCellBlock;
import net.createplant.block.entity.ElectrolysisCellBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/** The electrolyte and, while gas is evolving, its bubbles; the electrodes belong to the head above. */
public class ElectrolysisCellRenderer extends SafeBlockEntityRenderer<ElectrolysisCellBlockEntity> {
    private static final PartialModel BUBBLE = part("bubble");
    private static PartialModel part(String name) { return PartialModel.of(CreatePlant.location("block/electrolysis_cell/" + name)); }
    public static void init() { }
    public ElectrolysisCellRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(ElectrolysisCellBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = be.getBlockState();
        var owner = be.controller();
        var display = owner == null ? null : ElectrolysisDisplayState.sample(owner, AnimationTickHolder.getRenderTime(be.getLevel()));
        var feed = owner == null ? be.tanks.getFirst().getFluid() : owner.fluid(0);
        if (feed.isEmpty()) return;
        float fraction = owner == null ? (float) feed.getAmount() / ElectrolysisCellBlockEntity.CAPACITY
            : display.value(ElectrolysisDisplayState.FEED);
        float y0 = .125f, y1 = Math.min(.875f, .125f + .75f * fraction);
        if (y1 <= y0) return;
        float x0 = inset(be, Direction.WEST), z0 = inset(be, Direction.NORTH);
        float x1 = 1 - inset(be, Direction.EAST), z1 = 1 - inset(be, Direction.SOUTH);
        var translucent = new VertexConsumerWrapper(FluidRenderHelper.getFluidBuilder(buffers)) {
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { parent.setColor(r, g, b, a * 2 / 3);return this; }
        };
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(feed, x0, y0, z0, x1, y1, z1, translucent, pose, light, false, true);
        if (owner == null || owner.running() == 0 || owner.metalRate() > 0) return;
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        var bubbles = buffers.getBuffer(RenderType.solid());
        for (int i = 0; i < 5; i++) {
            double x = .25 + (i % 3) * .2, z = .22 + (i % 4) * .16;
            double y = y0 + ((time / 38 + i * .17 + be.getBlockPos().getZ() * .13) % 1) * (y1 - y0);
            pose.pushPose();pose.translate(x, y, z);
            CachedBuffers.partial(BUBBLE, state).color(0xc4e7e5).light(light).renderInto(pose, bubbles);
            pose.popPose();
        }
    }

    private static float inset(ElectrolysisCellBlockEntity be, Direction side) {
        return be.getBlockState().getValue(ElectrolysisCellBlock.JOIN.get(side.ordinal())) ? 0 : .125f;
    }
    @Override public AABB getRenderBoundingBox(ElectrolysisCellBlockEntity be) { return new AABB(be.getBlockPos()).inflate(.2, .8, .2); }
}
