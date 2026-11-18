package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.FuelCycleMachineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class FuelCycleMachineRenderer extends KineticBlockEntityRenderer<FuelCycleMachineBlockEntity> {
    private static final PartialModel PLUNGER = PartialModel.of(CreatePlant.location("block/plant_systems/pulse_plunger"));
    private static final PartialModel POUR = PartialModel.of(CreatePlant.location("block/plant_systems/pour_stream"));
    public static void init() { }
    public FuelCycleMachineRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @Override protected void renderSafe(FuelCycleMachineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (!be.melter()) {
            super.renderSafe(be, partialTicks, ms, buffers, light, overlay);
            double offset = be.formed() && Math.abs(be.getSpeed()) >= 32 ? Math.sin(AnimationTickHolder.getRenderTime(be.getLevel()) * be.getSpeed() / 192) * .12 : 0;
            CachedBuffers.partial(PLUNGER, be.getBlockState()).translate(0, offset, 0).light(light).renderInto(ms, buffers.getBuffer(RenderType.solid()));
        } else if (be.status().equals("vitrifying") && be.progress() > 1000) {
            CachedBuffers.partial(POUR, be.getBlockState()).translate(0, -.5, 0).light(0xF000F0).renderInto(ms, buffers.getBuffer(RenderType.solid()));
        }
    }
}
