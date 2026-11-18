package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.block.TankFootprint;
import net.createplant.block.TankStructure;
import net.createplant.block.entity.TankCasingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws the stored fluid's surface across the tank interior at the height its fill fraction reaches. */
public class TankCasingRenderer implements BlockEntityRenderer<TankCasingBlockEntity> {
    private static final float MIN_DEPTH = 1 / 16f;

    public TankCasingRenderer(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(TankCasingBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        TankStructure.Shape shape = be.getShape();
        FluidStack fluid = be.getFluid();
        float fill = be.getFluidLevel(partialTick);
        if (shape == null || fluid.isEmpty() || fill <= 0 || be.getLevel() == null)
            return;

        int height = shape.height();
        float y = 1 + Math.max(MIN_DEPTH, fill * height);
        BlockPos center = be.getBlockPos();
        // The center floor casing is opaque, so light is sampled in the interior cell holding the surface.
        int surfaceLight = LevelRenderer.getLightColor(be.getLevel(),
            center.above(1 + Math.min(height - 1, (int) (fill * height))));
        int luminosity = fluid.getFluidType().getLightLevel(fluid);
        surfaceLight = LightTexture.pack(Math.max(LightTexture.block(surfaceLight), luminosity),
            LightTexture.sky(surfaceLight));

        IClientFluidTypeExtensions fluidType = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(fluidType.getStillTexture(fluid));
        int color = fluidType.getTintColor(fluid);
        VertexConsumer buffer = FluidRenderHelper.getFluidBuilder(buffers);

        BlockPos origin = shape.origin();
        int ox = origin.getX() - center.getX();
        int oz = origin.getZ() - center.getZ();
        int diameter = shape.diameter();
        // Each row of the circle's interior is one contiguous strip, tiled at block scale like Create's tanks.
        for (int x = 1; x < diameter - 1; x++) {
            int z0 = 0;
            while (z0 < diameter && !isInterior(x, z0, diameter))
                z0++;
            if (z0 == diameter)
                continue;
            int z1 = z0;
            while (isInterior(x, z1 + 1, diameter))
                z1++;
            FluidRenderHelper.renderStillTiledFace(Direction.UP, ox + x, oz + z0, ox + x + 1, oz + z1 + 1, y,
                buffer, pose, surfaceLight, color, sprite);
        }
    }

    private static boolean isInterior(int x, int z, int diameter) {
        return TankFootprint.contains(x, z, diameter) && !TankFootprint.isWall(x, z, diameter);
    }

    @Override
    public boolean shouldRenderOffScreen(TankCasingBlockEntity be) {
        return be.getShape() != null;
    }

    @Override
    public AABB getRenderBoundingBox(TankCasingBlockEntity be) {
        TankStructure.Shape shape = be.getShape();
        if (shape == null)
            return new AABB(be.getBlockPos());
        BlockPos origin = shape.origin();
        return new AABB(origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + shape.diameter(), origin.getY() + shape.height() + 1, origin.getZ() + shape.diameter());
    }
}
