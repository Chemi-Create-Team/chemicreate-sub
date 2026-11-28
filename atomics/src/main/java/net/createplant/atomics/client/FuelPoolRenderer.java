package net.createplant.atomics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.block.entity.FuelPoolBlockEntity;
import net.createplant.atomics.nuclear.FuelPoolStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

public class FuelPoolRenderer extends SafeBlockEntityRenderer<FuelPoolBlockEntity> {
    public FuelPoolRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(FuelPoolBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) return;
        var vb = buffer.getBuffer(RenderType.cutout());
        for (int y = 0; y < net.createplant.atomics.nuclear.ReactorPhysics.FUEL_ASSEMBLY_LENGTH; y++) {
            int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(y + 1));
            for (int i = 0; i < FuelChannelBlockEntity.HOLES.length; i++) if (be.slot(i) != FuelPoolBlockEntity.EMPTY)
                CachedBuffers.partial(FuelChannelRenderer.TUBE, be.getBlockState()).translate(FuelChannelBlockEntity.HOLES[i][0], y + .25f, FuelChannelBlockEntity.HOLES[i][1])
                    .light(lit).renderInto(ms, vb);
        }
        if (be.waterLevel() <= 0) return;
        float y = .25f + (FuelPoolStructure.DEPTH + .5f) * be.waterLevel();
        int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above((int) y));
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation.withDefaultNamespace("block/water_still"));
        var fluidBuffer = FluidRenderHelper.getFluidBuilder(buffer);
        FluidRenderHelper.renderStillTiledFace(Direction.UP, 0, 0, 1, 1, y, fluidBuffer, ms, lit, 0xB34A9DA9, sprite);
        FluidRenderHelper.renderStillTiledFace(Direction.DOWN, 0, 0, 1, 1, y, fluidBuffer, ms, lit, 0xB34A9DA9, sprite);
    }

    @Override public boolean shouldRenderOffScreen(FuelPoolBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(FuelPoolBlockEntity be) { return new AABB(be.getBlockPos()).expandTowards(0, FuelPoolStructure.DEPTH, 0); }
}
