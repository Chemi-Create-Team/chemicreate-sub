package net.createplant.logistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createplant.CreatePlant;
import net.createplant.logistics.arm.ArmPattern;
import net.createplant.logistics.arm.ArmPose;
import net.createplant.logistics.arm.ArmTool;
import net.createplant.logistics.block.entity.IndustrialArmBlockEntity;
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
 * Draws the arm with Create's joint transforms and a slimmer working head.
 */
public class IndustrialArmRenderer implements BlockEntityRenderer<IndustrialArmBlockEntity> {
    private static final PartialModel JAW = PartialModel.of(CreatePlant.location("block/industrial_arm/precision_jaw"));
    private static final PartialModel STEM = PartialModel.of(CreatePlant.location("block/industrial_arm/precision_extension"));
    private static final PartialModel TORCH = PartialModel.of(CreatePlant.location("block/industrial_arm/torch_tip"));
    private static final float TOOL_SIZE = .375f, TOOL_REACH = .5f;
    private static final float PRECISION_EXTENSION = .105f;

    public IndustrialArmRenderer(BlockEntityRendererProvider.Context ctx) {}
    public static void init() {}

    @Override
    public AABB getRenderBoundingBox(IndustrialArmBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(IndustrialArmBlockEntity.RANGE + 1);
    }

    @Override
    public void render(IndustrialArmBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.phase == IndustrialArmBlockEntity.MOVE || be.phase == IndustrialArmBlockEntity.WORK)
            ArmWorkDraw.draw(be, ps, buffers, light, be.getLevel().getGameTime() + partial);
        VertexConsumer vb = buffers.getBuffer(RenderType.solid());
        CachedBuffers.partial(AllPartialModels.ARM_COG, state).rotateCentered(KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), Direction.Axis.Y), Direction.UP).light(light).renderInto(ps, vb);

        PoseStack local = new PoseStack();
        PoseTransformStack msr = TransformStack.of(local);
        msr.center();
        float base = be.baseAngle.getValue(partial), lower = be.lowerArmAngle.getValue(partial) - 135, upper = be.upperArmAngle.getValue(partial) - 90, head = be.headAngle.getValue(partial);

        ArmTool tool = be.toolType();
        ArmPattern pattern = be.displayedPattern();
        boolean helper = be.phase == IndustrialArmBlockEntity.ASSIST;
        boolean precision = pattern == ArmPattern.CORE_SET || pattern == ArmPattern.WIRE_THREAD;
        boolean working = be.phase == IndustrialArmBlockEntity.WORK;
        float extension = be.wristLift.getValue(partial) + (helper || working && precision ? PRECISION_EXTENSION : 0);
        ItemStack shown = be.phase == IndustrialArmBlockEntity.FETCH || precision && working ? ItemStack.EMPTY : be.held;
        boolean has = !shown.isEmpty(), block = shown.getItem() instanceof BlockItem;

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
        local.pushPose();
        msr.rotateZDegrees(180);
        local.scale(.58f, .54f, 1);
        part(AllPartialModels.ARM_CLAW_BASE, state, light, local, ps, vb);
        local.popPose();

        if (tool == ArmTool.HAND) {
            float jawAngle = block ? 42 : has ? 14 : 12;
            if (helper || working && pattern == ArmPattern.WIRE_THREAD) jawAngle = 0;
            if (working && pattern == ArmPattern.CORE_SET) {
                double time = (be.getLevel().getGameTime() % 24 + partial) * Math.PI / 3;
                jawAngle = 3 + (float) (7 * (1 - Math.cos(time)));
            }
            if (extension > .001f) {
                local.pushPose();
                local.translate(0, 0, -.25);
                local.scale(1, 1, extension);
                part(STEM, state, light, local, ps, vb);
                local.popPose();
            }
            jaw(state, light, local, ps, vb, 1, jawAngle, extension);
            jaw(state, light, local, ps, vb, -1, jawAngle, extension);
        } else {
            local.pushPose();
            if (working) local.translate(0, 0, -.075);
            part(TORCH, state, light, local, ps, vb);
            local.popPose();
        }

        if (has) {
            msr.rotateXDegrees(90);
            float s = block ? .5f : precision ? .16f : TOOL_SIZE;
            local.translate(0, tool != ArmTool.HAND ? -(TOOL_REACH + .15) : block ? -.5625 : -(TOOL_REACH + extension - s / 2), 0);
            local.scale(s, s, s);
            ps.pushPose();
            ps.last().pose().mul(local.last().pose());
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
        }
    }

    private static void jaw(BlockState state, int light, PoseStack local, PoseStack ps, VertexConsumer vb, int side, float angle, float extension) {
        local.pushPose();
        local.translate(0, side * .0425, -.25 - extension);
        if (side < 0) local.mulPose(Axis.ZP.rotationDegrees(180));
        local.mulPose(Axis.XP.rotationDegrees(angle));
        part(JAW, state, light, local, ps, vb);
        local.popPose();
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
