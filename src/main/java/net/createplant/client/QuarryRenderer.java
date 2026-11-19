package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.QuarryDriveBlockEntity;
import net.createplant.block.entity.QuarryConveyor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the moving parts of a gantry quarry from its drive: the bridge and its belt, the trolley, the rigid drill stem, the drill
 * head with its bin, the bellows chute from the bin up to the bridge, and the parcels rising in the chute and crossing the belt.
 */
public class QuarryRenderer extends KineticBlockEntityRenderer<QuarryDriveBlockEntity> {
    public static final PartialModel GIRDER = PartialModel.of(CreatePlant.location("block/crane/quarry_girder"));
    public static final PartialModel END_TRUCK = PartialModel.of(CreatePlant.location("block/crane/quarry_end_truck"));
    public static final PartialModel TROLLEY = PartialModel.of(CreatePlant.location("block/crane/quarry_trolley"));
    public static final PartialModel DRILL_STEM = PartialModel.of(CreatePlant.location("block/crane/quarry_drill_stem_segment"));
    public static final PartialModel GRAPPLE = PartialModel.of(CreatePlant.location("block/crane/quarry_grapple"));
    public static final PartialModel HEAD_BIN = PartialModel.of(CreatePlant.location("block/crane/head_bin"));
    public static final PartialModel CHUTE_RING = PartialModel.of(CreatePlant.location("block/crane/chute_ring"));
    public static final PartialModel CHUTE_FOLD = PartialModel.of(CreatePlant.location("block/crane/chute_fold"));
    public static final PartialModel BRIDGE_BELT = PartialModel.of(CreatePlant.location("block/crane/bridge_belt"));
    public static final PartialModel BRIDGE_BELT_FRAME = PartialModel.of(CreatePlant.location("block/crane/bridge_belt_frame"));
    private static final double FOLD = .125;

    public QuarryRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    public static void init() { }

    @Override protected BlockState getRenderedBlockState(QuarryDriveBlockEntity be) { return shaft(be.getBlockState().getValue(BlockStateProperties.AXIS)); }

    @Override public boolean shouldRenderOffScreen(QuarryDriveBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 128; }

    @Override public boolean shouldRender(QuarryDriveBlockEntity be,Vec3 camera) {
        var bounds=be.getRenderBoundingBox();
        double x=camera.x-Math.clamp(camera.x,bounds.minX,bounds.maxX),y=camera.y-Math.clamp(camera.y,bounds.minY,bounds.maxY),z=camera.z-Math.clamp(camera.z,bounds.minZ,bounds.maxZ);
        return x*x+y*y+z*z<128*128;
    }

    @Override protected void renderSafe(QuarryDriveBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        var level = be.getLevel();
        if (level == null) return;
        BlockPos obstruction = be.constructionObstruction();
        if (obstruction != null && level.hasChunkAt(obstruction) && !level.isEmptyBlock(obstruction))
            Outliner.getInstance().showAABB(be, new AABB(obstruction).inflate(1 / 64d)).colored(0xff403a).lineWidth(1 / 16f).disableLineNormals();
        else Outliner.getInstance().remove(be);
        var quarry = be.visibleStructure();
        if (quarry == null) return;
        BlockState state = be.getBlockState();
        VertexConsumer solid = buffer.getBuffer(RenderType.cutout());
        double u = be.renderedU(partialTicks), v = be.renderedV(partialTicks), depth = be.renderedDepth(partialTicks);
        Vec3 along = Vec3.atLowerCornerOf(quarry.along().getNormal()), across = Vec3.atLowerCornerOf(quarry.across().getNormal());
        Vec3 origin = Vec3.atLowerCornerOf(quarry.start().subtract(be.getBlockPos())).add(along.scale(u)).add(0, 1, 0);
        // The girder and belt models run along X; turn them to lie across the rails.
        float yaw = quarry.across().getAxis() == Direction.Axis.X ? 0 : 90;
        for (int i = 0; i <= quarry.span(); i++)
            CraneRenderer.draw(CachedBuffers.partial(i == 0 || i == quarry.span() ? END_TRUCK : GIRDER, state), origin.add(across.scale(i)), yaw, be, ms, solid);
        var shift=AllSpriteShifts.BELT;
        int direction=quarry.across().getAxis()==Direction.Axis.X ? -quarry.across().getStepX() : quarry.across().getStepZ();
        float scroll=(float)(be.renderedBeltTravel(partialTicks)%1)*direction*.5f*(shift.getTarget().getV1()-shift.getTarget().getV0());
        for (int i = -1; i <= quarry.span(); i++) {
            Vec3 at=origin.add(across.scale(i));
            CraneRenderer.draw(CachedBuffers.partial(BRIDGE_BELT_FRAME,state),at,yaw,be,ms,solid);
            CraneRenderer.draw(CachedBuffers.partial(BRIDGE_BELT,state).shiftUVScrolling(shift,scroll),at,yaw,be,ms,solid);
        }
        Vec3 trolley = origin.add(across.scale(v));
        CraneRenderer.draw(CachedBuffers.partial(TROLLEY, state), trolley, yaw, be, ms, solid);
        double hookY = -1 - depth;
        for (double y = hookY + 1; y < 0; y += 1) if (Math.min(y + 1, 0) - y >= 0.05)
            CraneRenderer.draw(CachedBuffers.partial(DRILL_STEM, state), trolley.add(0, y, 0), 0, be, ms, solid);
        Vec3 hook = trolley.add(0, hookY, 0);
        if (be.drill()) {
            float spin = be.working() ? AnimationTickHolder.getRenderTime(be.getLevel()) * 20 % 360 : 0;
            CraneRenderer.draw(CachedBuffers.partialFacing(AllPartialModels.DRILL_HEAD, state, Direction.DOWN), hook, spin, be, ms, solid);
            CraneRenderer.draw(CachedBuffers.partial(HEAD_BIN, state), hook, spoutYaw(quarry.across().getOpposite()), be, ms, solid);
        } else CraneRenderer.draw(CachedBuffers.partial(GRAPPLE, state), hook, yaw, be, ms, solid);

        // The chute stands beside the stem, on the side the bridge belt runs to, from the bin's spout up to the belt.
        Vec3 bottom = hook.add(.5, 1, .5).subtract(across.scale(QuarryConveyor.CHUTE_OFFSET)), top = trolley.add(.5, QuarryConveyor.BELT_HEIGHT + QuarryConveyor.ITEM_CLEARANCE, .5).subtract(across.scale(QuarryConveyor.CHUTE_OFFSET));
        for (double y = bottom.y; y <= top.y; y += FOLD) {
            int fold = (int) Math.round((y - bottom.y) / FOLD);
            CraneRenderer.draw(CachedBuffers.partial(fold % 2 == 0 ? CHUTE_RING : CHUTE_FOLD, state), new Vec3(bottom.x - .5, y - .5, bottom.z - .5), 0, be, ms, solid);
        }
        long now = be.conveyorTime();
        float conveyorPartial = be.conveyorPartial(partialTicks);
        var items = Minecraft.getInstance().getItemRenderer();
        boolean shownBridgeEnd = false, shownChuteTop = false;
        int candidates = 0;
        for (var parcel : be.conveyor().parcels()) {
            if (candidates++ == QuarryConveyor.MAX_VISIBLE_PARCELS) break;
            double p = parcel.progress(now, conveyorPartial);
            if (p == 1) {
                if (parcel.bridge() ? shownBridgeEnd : shownChuteTop) continue;
                if (parcel.bridge()) shownBridgeEnd = true;
                else shownChuteTop = true;
            }
            Vec3 at = parcel.bridge()
                ? origin.add(across.scale(v - QuarryConveyor.CHUTE_OFFSET + (-1 - v + QuarryConveyor.CHUTE_OFFSET) * p)).add(.5, QuarryConveyor.BELT_HEIGHT + QuarryConveyor.ITEM_CLEARANCE, .5)
                : bottom.add(0, (top.y - bottom.y) * p, 0);
            BlockPos lit = be.getBlockPos().offset((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
            int seed = Long.hashCode(parcel.start());
            ms.pushPose();
            ms.translate(at.x, at.y, at.z);
            ms.mulPose(Axis.YP.rotationDegrees(parcel.bridge() ? yaw : (now % 90 + conveyorPartial) * 4 + (seed & 7) * 47));
            ms.scale(.35f, .35f, .35f);
            items.renderStatic(parcel.stack(), ItemDisplayContext.FIXED, LevelRenderer.getLightColor(be.getLevel(), lit), OverlayTexture.NO_OVERLAY, ms, buffer, be.getLevel(), seed);
            ms.popPose();
        }
    }

    /** The yaw that turns a model's +X towards {@code side}, for the bin's spout under the chute. */
    private static float spoutYaw(Direction side) {
        return switch (side) { case NORTH -> 90; case WEST -> 180; case SOUTH -> 270; default -> 0; };
    }
}
