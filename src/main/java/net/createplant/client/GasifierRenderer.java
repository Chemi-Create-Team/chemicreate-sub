package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.CreatePlant;
import net.createplant.block.entity.GasifierBlockEntity;
import net.createplant.registry.CEGasifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class GasifierRenderer extends SafeBlockEntityRenderer<GasifierBlockEntity> {
    public GasifierRenderer(BlockEntityRendererProvider.Context context) { }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CEGasifier.PART.get(), GasifierRenderer::new);
    }

    @Override protected void renderSafe(GasifierBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.formed()) return;
        var water = be.quenchWater();
        if (water.isEmpty()) return;
        float level = 1.125f + .75f * Math.min(4000, water.getAmount()) / 4000f;
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(water, .025f, 1.125f, .025f, .975f, level, .975f,
            FluidRenderHelper.getFluidBuilder(buffers), pose, light, false, true);
    }

    @Override public AABB getRenderBoundingBox(GasifierBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }
}
