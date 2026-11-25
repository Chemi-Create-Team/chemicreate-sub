package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createplant.arm.ArmTool;
import net.createplant.block.entity.IndustrialArmBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the arm from Create's own partial models and joint transforms, so it moves exactly like a mechanical arm. A yellow tint
 * and the tool at the claw tell it apart. Plain block entity rendering: no Flywheel instancing.
 */
public class IndustrialArmRenderer implements BlockEntityRenderer<IndustrialArmBlockEntity> {
    private static final int TINT = 0xFFC060;

    public IndustrialArmRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public AABB getRenderBoundingBox(IndustrialArmBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2);
    }

    @Override
    public void render(IndustrialArmBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partial(AllPartialModels.ARM_COG, state).color(TINT).rotateCentered(KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), Direction.Axis.Y), Direction.UP).light(light).renderInto(ps, vb);

        PoseStack local = new PoseStack();
        PoseTransformStack msr = TransformStack.of(local);
        msr.center();
        float base = be.baseAngle.getValue(partial), lower = be.lowerArmAngle.getValue(partial) - 135, upper = be.upperArmAngle.getValue(partial) - 90, head = be.headAngle.getValue(partial);

        ArmTool tool = be.toolType();
        ItemStack shown = tool == null ? ItemStack.EMPTY : tool == ArmTool.GRIPPER ? be.held : be.tool();
        boolean has = !shown.isEmpty(), block = has && shown.getItem() instanceof BlockItem;

        ArmRenderer.transformBase(msr, base);
        part(AllPartialModels.ARM_BASE, state, light, local, ps, vb);
        ArmRenderer.transformLowerArm(msr, lower);
        part(AllPartialModels.ARM_LOWER_BODY, state, light, local, ps, vb);
        ArmRenderer.transformUpperArm(msr, upper);
        part(AllPartialModels.ARM_UPPER_BODY, state, light, local, ps, vb);
        ArmRenderer.transformHead(msr, head);
        msr.rotateZDegrees(180);
        part(AllPartialModels.ARM_CLAW_BASE, state, light, local, ps, vb);
        msr.rotateZDegrees(180);
        if (tool == ArmTool.GRIPPER || tool == null) {
            for (int i : new int[] {1, -1}) {
                local.pushPose();
                ArmRenderer.transformClawHalf(msr, has, block, i);
                part(i > 0 ? AllPartialModels.ARM_CLAW_GRIP_LOWER : AllPartialModels.ARM_CLAW_GRIP_UPPER, state, light, local, ps, vb);
                local.popPose();
            }
        }
        if (has) {
            ps.pushPose();
            TransformStack.of(ps).rotateXDegrees(90);
            ps.translate(0, block ? -.5625 : -.625, 0);
            float s = block ? .5f : .625f;
            ps.scale(s, s, s);
            ps.last().pose().mul(local.last().pose());
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
        }
    }

    private static void part(PartialModel model, BlockState state, int light, PoseStack local, PoseStack ps, VertexConsumer vb) {
        SuperByteBuffer b = CachedBuffers.partial(model, state).color(TINT).light(light);
        b.transform(local);
        b.renderInto(ps, vb);
    }
}
