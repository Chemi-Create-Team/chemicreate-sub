package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.CreatePlant;
import net.createplant.block.WetProcessStructure;
import net.createplant.block.entity.WetProcessBlockEntity;
import net.createplant.registry.CEWetProcessing;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.fluids.FluidStack;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WetProcessRenderer extends KineticBlockEntityRenderer<WetProcessBlockEntity> {
    private static final PartialModel SHAFT = part("shaft"), BRIDGE = part("bridge"), SCRAPER = part("scraper"), WELL = part("feedwell");
    private static PartialModel part(String name) { return PartialModel.of(CreatePlant.location("block/wet_processing/" + name)); }
    public static void init() { }
    public WetProcessRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(CEWetProcessing.DRIVE_ENTITY.get(), WetProcessRenderer::new); }
    @Override protected void renderSafe(WetProcessBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.formed() || be.width() == 0) return;
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float angle = (time * be.getSpeed() * 3 / 10) % 360;
        float depth = be.height() - 1.25f, bottom = 2 - be.height() + .125f;
        int radius = be.width() / 2;
        draw(WELL, be, pose, buffers, light, 0, bottom + .35f, 0, 1, depth - .35f, 1, 0);
        draw(BRIDGE, be, pose, buffers, light, 0, .8f, 0, be.width(), 1, 1, angle);
        draw(SCRAPER, be, pose, buffers, light, 0, bottom + .15f, 0, be.width() - (be.width() % 2 == 0 ? 3 : 2), 1, 1, angle);
        draw(SHAFT, be, pose, buffers, light, 0, bottom, 0, 1, depth + .8f, 1, angle);
        FluidStack dirty = be.tanks[0].getFluid();
        int amount = be.tanks[0].getFluidAmount() + be.tanks[2].getFluidAmount();
        if (dirty.isEmpty() && be.tanks[2].isEmpty()) return;
        FluidStack clear = be.tanks[2].isEmpty() ? dirty : be.tanks[2].getFluid();
        if (dirty.isEmpty()) dirty = clear;
        float top = bottom + Math.min(1, (float) amount / be.capacity()) * depth;
        var liquid = FluidRenderHelper.getFluidBuilder(buffers);
        pose.pushPose();pose.translate(.5, 0, .5);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-be.getBlockState().getValue(net.createplant.block.WetProcessDriveBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        for (int x = WetProcessStructure.minimum(be.width()); x <= radius; x++) for (int z = WetProcessStructure.minimum(be.width()); z <= radius; z++) {
            if (!WetProcessStructure.interior(x, z, be.width())) continue;
            float split = bottom + (top - bottom) * .25f;
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(dirty, x, bottom, z, x + 1, split, z + 1, liquid, pose, light, false, true);
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(x * x + z * z <= 1 ? dirty : clear, x, split, z, x + 1, top, z + 1, liquid, pose, light, false, true);
        }
        pose.popPose();
    }
    private static void draw(PartialModel model, WetProcessBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light,
        float x, float y, float z, float sx, float sy, float sz, float angle) {
        pose.pushPose();pose.translate(.5 + x, y, .5 + z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));pose.scale(sx, sy, sz);pose.translate(-.5, 0, -.5);
        CachedBuffers.partial(model, be.getBlockState()).light(light).renderInto(pose, buffers.getBuffer(RenderType.solid()));pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(WetProcessBlockEntity be) { return be.getRenderBoundingBox(); }
}
