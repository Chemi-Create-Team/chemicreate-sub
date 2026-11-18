package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A slider-crank and crosshead driven by the actual Create shaft angle, in the engine's mounting frame. */
public class ReciprocatingCompressorRenderer extends SafeBlockEntityRenderer<ReciprocatingCompressorBlockEntity> {
    private static final PartialModel PISTON = part("piston"), LINKAGE = part("linkage"), CONNECTOR = part("crank");
    private static final double CRANK_RADIUS = .25, ROD_LENGTH = 1.5;
    private static PartialModel part(String name) {
        return PartialModel.of(CreatePlant.location("block/reciprocating_compressor/" + name));
    }

    public ReciprocatingCompressorRenderer(BlockEntityRendererProvider.Context context) { }

    /** Partial models must exist before the first model bake. */
    public static void init() { }

    /** The engine's blockstate rotation: the models are drawn in its own frame, crank above and crank axis along X. */
    private static Quaternionf turnOf(BlockState state) {
        AttachFace face = state.getValue(ReciprocatingCompressorBlock.FACE);
        Direction facing = state.getValue(ReciprocatingCompressorBlock.FACING);
        float x = face == AttachFace.CEILING ? 180 : face == AttachFace.WALL ? 90 : 0;
        float y = face == AttachFace.CEILING ? facing.toYRot() : facing.toYRot() + 180;
        return new Quaternionf().rotateYXZ(-y * Mth.DEG_TO_RAD, -x * Mth.DEG_TO_RAD, 0);
    }

    @Override protected void renderSafe(ReciprocatingCompressorBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = be.getLevel();
        if (level == null || !(level.getBlockEntity(be.shaftPos()) instanceof ReciprocatingShaftBlockEntity shaft)) return;
        BlockState state = be.getBlockState();
        // Facing engines are turned half a revolution about the shaft; the crank must still follow the shaft's own sense of rotation.
        var crankAxis = new Vector3f(1, 0, 0).rotate(turnOf(state));
        var axis = ReciprocatingCompressorBlock.shaftAxis(state);
        float sense = crankAxis.get(axis.ordinal()) < 0 ? -1 : 1;
        float shaftDegrees = sense * Mth.RAD_TO_DEG * KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), axis);
        float angle = -Mth.DEG_TO_RAD * (shaftDegrees % 360);
        double dz = CRANK_RADIUS * Mth.cos(angle);
        double dy = Math.sqrt(ROD_LENGTH * ROD_LENGTH - dz * dz);
        double pinY = 2.5 - CRANK_RADIUS * Mth.sin(angle) - dy;
        float lean = (float) Math.atan2(dz, dy);
        var vertices = buffers.getBuffer(RenderType.solid());
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(turnOf(state));
        pose.translate(-.5, -.5, -.5);
        CachedBuffers.partial(PISTON, state).translate(0, pinY - 1 / 16d, 0).light(light).renderInto(pose, vertices);
        pose.pushPose();
        pose.translate(.5, pinY, .5);
        pose.mulPose(Axis.XP.rotation(lean));
        pose.translate(-.5, 0, -.5);
        CachedBuffers.partial(LINKAGE, state).light(light).renderInto(pose, vertices);
        pose.popPose();
        CachedBuffers.partial(CONNECTOR, state)
            .translate(0, 2, 0)
            .center()
            .rotateX(angle)
            .uncenter()
            .light(light)
            .renderInto(pose, vertices);
        pose.popPose();
    }
}
