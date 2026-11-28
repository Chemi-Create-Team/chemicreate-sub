package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Draws one gantry part of a crane or quarry at an offset from its block entity. */
public final class GantryRenderHelper {
    private GantryRenderHelper() { }

    public static void draw(SuperByteBuffer model, Vec3 at, float yaw, BlockEntity be, PoseStack ms, VertexConsumer consumer) {
        BlockPos lit = be.getBlockPos().offset((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        model.translate(at.x, at.y, at.z).center().rotateYDegrees(yaw).uncenter()
            .light(LevelRenderer.getLightColor(be.getLevel(), lit)).renderInto(ms, consumer);
    }
}
