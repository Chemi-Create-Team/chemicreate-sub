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
import net.createplant.arm.ArmPose;
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
 * Draws the arm from Create's own partial models and joint transforms, so it moves exactly like a mechanical arm. Longer arm
 * segments and the tool at the claw tell it apart. Plain block entity rendering: no Flywheel instancing.
 */
public class IndustrialArmRenderer implements BlockEntityRenderer<IndustrialArmBlockEntity> {
    /** A tool is drawn this large in the claw's frame and ends where the claw aims, half a block past the wrist. */
    private static final float TOOL_SIZE = .375f, TOOL_REACH = .5f;

    public IndustrialArmRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public AABB getRenderBoundingBox(IndustrialArmBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(IndustrialArmBlockEntity.RANGE + 1);
    }

    @Override
    public void render(IndustrialArmBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffers.getBuffer(RenderType.solid());
        if (be.phase == IndustrialArmBlockEntity.MOVE || be.phase == IndustrialArmBlockEntity.WORK)
            ArmWorkDraw.draw(be, ps, buffers, light, be.getLevel().getGameTime() + partial);
        vb = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partial(AllPartialModels.ARM_COG, state).rotateCentered(KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), Direction.Axis.Y), Direction.UP).light(light).renderInto(ps, vb);

        PoseStack local = new PoseStack();
        PoseTransformStack msr = TransformStack.of(local);
        msr.center();
        float base = be.baseAngle.getValue(partial), lower = be.lowerArmAngle.getValue(partial) - 135, upper = be.upperArmAngle.getValue(partial) - 90, head = be.headAngle.getValue(partial);

        ArmTool tool = be.toolType();
        ItemStack shown = tool != ArmTool.HAND ? be.tool() : be.phase == IndustrialArmBlockEntity.FETCH ? ItemStack.EMPTY : be.held;
        boolean has = !shown.isEmpty(), block = has && shown.getItem() instanceof BlockItem;
        boolean grip = has || be.phase == IndustrialArmBlockEntity.ASSIST; // a helper's claw closes on the wire it holds

        ArmRenderer.transformBase(msr, base);
        part(AllPartialModels.ARM_BASE, state, light, local, ps, vb);
        ArmRenderer.transformLowerArm(msr, lower);
        stretched(AllPartialModels.ARM_LOWER_BODY, state, light, local, ps, vb);
        // Create's joint offsets lie along the segment's -Z; only the segments get longer, the joints keep Create's size.
        local.translate(0, 0, -ArmPose.LOWER * (ArmPose.SCALE - 1));
        ArmRenderer.transformUpperArm(msr, upper);
        stretched(AllPartialModels.ARM_UPPER_BODY, state, light, local, ps, vb);
        local.translate(0, 0, -ArmPose.UPPER * (ArmPose.SCALE - 1));
        ArmRenderer.transformHead(msr, head);
        msr.rotateZDegrees(180);
        part(AllPartialModels.ARM_CLAW_BASE, state, light, local, ps, vb);
        msr.rotateZDegrees(180);
        if (tool == ArmTool.HAND) {
            for (int i : new int[] {1, -1}) {
                local.pushPose();
                ArmRenderer.transformClawHalf(msr, grip, block, i);
                part(i > 0 ? AllPartialModels.ARM_CLAW_GRIP_LOWER : AllPartialModels.ARM_CLAW_GRIP_UPPER, state, light, local, ps, vb);
                local.popPose();
            }
        }
        // A tool arm carrying finished belt work: the item hangs just under the tool.
        ItemStack carried = tool != ArmTool.HAND && be.phase == IndustrialArmBlockEntity.CARRY ? be.held : ItemStack.EMPTY;
        if (!carried.isEmpty()) {
            local.pushPose();
            msr.rotateXDegrees(90);
            local.translate(0, -(TOOL_REACH + .15), 0);
            float s = carried.getItem() instanceof BlockItem ? .35f : .3f;
            local.scale(s, s, s);
            ps.pushPose();
            ps.last().pose().mul(local.last().pose());
            Minecraft.getInstance().getItemRenderer().renderStatic(carried, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
            local.popPose();
        }
        if (has) {
            // As Create's arm: the item hangs off the claw in the claw's own frame, so the offsets come after the joints.
            msr.rotateXDegrees(90);
            local.translate(0, block ? -.5625 : -(TOOL_REACH - TOOL_SIZE / 2), 0);
            float s = block ? .5f : TOOL_SIZE;
            local.scale(s, s, s);
            ps.pushPose();
            ps.last().pose().mul(local.last().pose());
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
        }
    }

    /** A segment drawn longer along its length only, so it keeps Create's thickness. */
    private static void stretched(PartialModel model, BlockState state, int light, PoseStack local, PoseStack ps, VertexConsumer vb) {
        local.pushPose();
        local.scale(1, 1, ArmPose.SCALE);
        part(model, state, light, local, ps, vb);
        local.popPose();
    }

    private static void part(PartialModel model, BlockState state, int light, PoseStack local, PoseStack ps, VertexConsumer vb) {
        SuperByteBuffer b = CachedBuffers.partial(model, state).light(light);
        b.transform(local);
        b.renderInto(ps, vb);
    }
}
