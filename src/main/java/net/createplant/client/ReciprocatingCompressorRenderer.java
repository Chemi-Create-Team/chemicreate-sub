package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.ReciprocatingCompressorBlock;
import net.createplant.block.entity.ReciprocatingCompressorBlockEntity;
import net.createplant.block.entity.ReciprocatingShaftBlockEntity;
import net.createplant.registry.CEReciprocating;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A slider-crank and crosshead driven by the actual Create shaft angle, in the engine's mounting frame. */
public class ReciprocatingCompressorRenderer extends SafeBlockEntityRenderer<ReciprocatingCompressorBlockEntity> {
    private static final PartialModel PISTON = part("piston"), LINKAGE = part("linkage"), CONNECTOR = part("crank");
    private static final PartialModel OPPOSED_CRANK = part("opposed_crank"), OPPOSED_ROD = part("opposed_rod"),
        CROSSHEAD = part("opposed_crosshead"), PISTON_ROD = part("opposed_piston_rod"),
        OPPOSED_PISTON = part("opposed_piston"), GUIDE = part("opposed_guide");
    private static final PartialModel CYLINDER = part("industrial_cylinder"), BED = part("industrial_bed"), INSTRUMENTS = part("industrial_instruments"),
        FLYWHEEL = part("industrial_flywheel_cover"), MOTOR = part("industrial_motor");
    private static final double CRANK_RADIUS = .25, ROD_LENGTH = 1.5;
    private static final double OPPOSED_THROW = 2.5 / 16, OPPOSED_ROD_LENGTH = 12 / 16d;
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
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (state.getValue(ReciprocatingCompressorBlock.OPPOSED)) {
            renderOpposed(be, state, pose, buffers.getBuffer(RenderType.solid()), light);
            return;
        }
        if (!(level.getBlockEntity(be.shaftPos()) instanceof ReciprocatingShaftBlockEntity shaft)) return;
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

    private static void renderOpposed(ReciprocatingCompressorBlockEntity be, BlockState state, PoseStack pose, VertexConsumer vertices, int light) {
        var level = be.getLevel();
        var centre = be.getBlockPos();
        Direction front = state.getValue(ReciprocatingCompressorBlock.FACING);
        int frontRows = neighbouringRows(level, centre, front, state);
        int rearRows = neighbouringRows(level, centre, front.getOpposite(), state);
        boolean bank = frontRows + rearRows + 1 == ReciprocatingCompressorBlock.MAX_ROWS;
        float angle = 0;
        var shaft = be.driveShaft();
        if (shaft != null)
            angle = front.getOpposite().getAxisDirection().getStep() * KineticBlockEntityRenderer.getAngleForBe(shaft, shaft.getBlockPos(), front.getAxis());
        if (bank) angle += frontRows * Mth.TWO_PI / 3;
        int sides = be.connectedSides();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - front.toYRot()));
        pose.translate(-.5, 0, -.5);
        if (state.getValue(ReciprocatingCompressorBlock.WINDOW) || sides != 3) {
            CachedBuffers.partial(AllPartialModels.SHAFT, state).center().rotateZ(angle).rotateX(Mth.HALF_PI)
                .scale(1, .875f, 1).uncenter().light(light).renderInto(pose, vertices);
            CachedBuffers.partial(OPPOSED_CRANK, state).center().rotateZ(angle).uncenter().light(light).renderInto(pose, vertices);
        }
        if (bank && !state.getValue(ReciprocatingCompressorBlock.WINDOW) && level.getBlockState(centre.above()).isAir())
            CachedBuffers.partial(INSTRUMENTS, state).light(light).renderInto(pose, vertices);
        if (bank && rearRows == 0 && shaft != null) {
            int shaftLight = LevelRenderer.getLightColor(level, shaft.getBlockPos());
            CachedBuffers.partial(FLYWHEEL, state).translate(0, 0, 1).light(shaftLight).renderInto(pose, vertices);
            var motor = shaft.getBlockPos().relative(front.getOpposite());
            if (level.getBlockEntity(motor) instanceof GeneratingKineticBlockEntity)
                CachedBuffers.partial(MOTOR, state).translate(0, 0, 2).light(LevelRenderer.getLightColor(level, motor)).renderInto(pose, vertices);
        }
        for (int side = 0; side < 2; side++) {
            if ((sides & (1 << side)) == 0) continue;
            var direction = ReciprocatingCompressorBlock.cylinderSide(state, side);
            var gap = centre.relative(direction);
            var cylinder = gap.relative(direction);
            if (!level.isLoaded(gap) || !level.getBlockState(gap).isAir()
                || !level.isLoaded(cylinder) || !level.getBlockState(cylinder).is(CEReciprocating.CYLINDER.get())) continue;
            int sign = side == 0 ? -1 : 1;
            int guideLight = LevelRenderer.getLightColor(level, gap), pistonLight = LevelRenderer.getLightColor(level, cylinder);
            renderSide(state, pose, vertices, angle, sign, guideLight, pistonLight);
            CachedBuffers.partial(CYLINDER, state).translate(sign * 2, 0, 0).light(pistonLight).renderInto(pose, vertices);
            if (bank) CachedBuffers.partial(BED, state).translate(sign, 0, 0).light(guideLight).renderInto(pose, vertices);
        }
        pose.popPose();
    }

    private static int neighbouringRows(Level level, BlockPos centre, Direction direction, BlockState state) {
        int count = 0;
        for (int i = 1; i <= ReciprocatingCompressorBlock.MAX_ROWS; i++) {
            var next = centre.relative(direction, i);
            if (!level.isLoaded(next) || !ReciprocatingCompressorBlock.sameLine(state, level.getBlockState(next))) break;
            count++;
        }
        return count;
    }

    private static void renderSide(BlockState state, PoseStack pose, VertexConsumer vertices, float angle, int sign, int light, int pistonLight) {
        double pinX = sign * OPPOSED_THROW * Math.cos(angle), pinY = sign * OPPOSED_THROW * Math.sin(angle);
        double span = sign * Math.sqrt(OPPOSED_ROD_LENGTH * OPPOSED_ROD_LENGTH - pinY * pinY);
        double crosshead = .5 + pinX + span;
        double piston = crosshead + sign * (2 - OPPOSED_ROD_LENGTH);
        CachedBuffers.partial(GUIDE, state).translate(sign, 0, 0).light(light).renderInto(pose, vertices);

        pose.pushPose();
        pose.translate(.5 + pinX, .5 + pinY, .5 + sign * (1.5 / 16));
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(-pinY, span)));
        CachedBuffers.partial(OPPOSED_ROD, state).light(light).renderInto(pose, vertices);
        pose.popPose();

        pose.pushPose();
        pose.translate(crosshead, .5, .5);
        if (sign < 0) pose.mulPose(Axis.YP.rotationDegrees(180));
        CachedBuffers.partial(CROSSHEAD, state).light(light).renderInto(pose, vertices);
        CachedBuffers.partial(PISTON_ROD, state).light(light).renderInto(pose, vertices);
        pose.popPose();
        CachedBuffers.partial(OPPOSED_PISTON, state).translate(piston, .5, .5).light(pistonLight).renderInto(pose, vertices);
    }
}
