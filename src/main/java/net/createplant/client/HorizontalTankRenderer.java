package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.block.entity.HorizontalTankBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class HorizontalTankRenderer extends FluidTankRenderer {
    public HorizontalTankRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    @Override protected void renderSafe(FluidTankBlockEntity be, float partialTicks, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (!(be instanceof HorizontalTankBlockEntity tank) || !tank.isController() || !tank.hasBoilerGauge()) return;
        boolean alongX = tank.getMainConnectionAxis() == Direction.Axis.X;
        int sizeX = alongX ? tank.getHeight() : tank.getWidth();
        int sizeZ = alongX ? tank.getWidth() : tank.getHeight();
        var level = tank.getLevel();
        var state = tank.getBlockState();
        var origin = tank.getBlockPos();
        var vertices = buffers.getBuffer(RenderType.cutout());
        float dial = 90 - 145 * tank.boilerGauge(partialTicks);
        poses.pushPose();
        poses.translate(sizeX / 2.0, .5, sizeZ / 2.0);
        for (Direction face : Iterate.horizontalDirections) {
            boolean faceX = face.getAxis() == Direction.Axis.X;
            int span = faceX ? sizeZ : sizeX;
            int edge = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? -1 : (faceX ? sizeX : sizeZ);
            int middle = (span - 1) / 2;
            BlockPos neighbour = faceX ? origin.offset(edge, 0, middle) : origin.offset(middle, 0, edge);
            if (covered(level, neighbour)) continue;
            if (span % 2 == 0 && covered(level, faceX ? neighbour.south() : neighbour.east())) continue;
            int faceLight = LevelRenderer.getLightColor(level, neighbour);
            float rotation = -face.toYRot() - 90;
            float projection = (faceX ? sizeX : sizeZ) / 2f - .375f;
            CachedBuffers.partial(AllPartialModels.BOILER_GAUGE, state).rotateYDegrees(rotation).uncenter()
                .translate(projection, 0, 0).light(faceLight).renderInto(poses, vertices);
            CachedBuffers.partial(AllPartialModels.BOILER_GAUGE_DIAL, state).rotateYDegrees(rotation).uncenter()
                .translate(projection, 0, 0).translate(0, .375f, .5f).rotateXDegrees(dial).translate(0, -.375f, -.5f)
                .light(faceLight).renderInto(poses, vertices);
        }
        poses.popPose();
    }

    private static boolean covered(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return true;
        var state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) || AllBlocks.STEAM_ENGINE.has(state);
    }
}
