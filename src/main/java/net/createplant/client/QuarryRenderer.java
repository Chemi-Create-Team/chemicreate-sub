package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.CraneStructure;
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
        double v = be.renderedV(partialTicks), depth = be.renderedDepth(partialTicks);
        Gantry gantry = Gantry.of(quarry, be.getBlockPos(), be.renderedU(partialTicks), v, depth);
        Vec3 origin = gantry.origin(), across = gantry.across(), bottom = gantry.chuteBottom(), top = gantry.chuteTop();
        float yaw = gantry.yaw();
        // Under a Flywheel backend QuarryVisual draws the gantry as instances; this draws it in Ponder and without a backend.
        if (!VisualizationManager.supportsVisualization(level)) {
            BlockState state = be.getBlockState();
            VertexConsumer solid = buffer.getBuffer(RenderType.cutout());
            parts(quarry, gantry, depth, be.drill(), (model, at, turn) -> CraneRenderer.draw(CachedBuffers.partial(model, state), at, turn, be, ms, solid));
            var shift=AllSpriteShifts.BELT;
            float scroll=(float)(be.renderedBeltTravel(partialTicks)%1)*beltDirection(quarry)*.5f*(shift.getTarget().getV1()-shift.getTarget().getV0());
            for (int i = -1; i <= quarry.span(); i++)
                CraneRenderer.draw(CachedBuffers.partial(BRIDGE_BELT,state).shiftUVScrolling(shift,scroll),origin.add(across.scale(i)),yaw,be,ms,solid);
            if (be.drill()) CraneRenderer.draw(CachedBuffers.partialFacing(AllPartialModels.DRILL_HEAD, state, Direction.DOWN), gantry.hook(), headSpin(be), be, ms, solid);
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

    /** Where the moving parts stand from the drive: the bridge's near end, the trolley, the drill head and both ends of the chute. */
    record Gantry(Vec3 across, Vec3 origin, Vec3 trolley, Vec3 hook, Vec3 chuteBottom, Vec3 chuteTop, float yaw) {
        static Gantry of(CraneStructure.Result quarry, BlockPos drive, double u, double v, double depth) {
            Vec3 along = Vec3.atLowerCornerOf(quarry.along().getNormal()), across = Vec3.atLowerCornerOf(quarry.across().getNormal());
            Vec3 origin = Vec3.atLowerCornerOf(quarry.start().subtract(drive)).add(along.scale(u)).add(0, 1, 0);
            Vec3 trolley = origin.add(across.scale(v)), hook = trolley.add(0, -1 - depth, 0), chute = across.scale(QuarryConveyor.CHUTE_OFFSET);
            // The girder and belt models run along X; turn them to lie across the rails.
            // The chute stands beside the stem, on the side the bridge belt runs to, from the bin's spout up to the belt.
            return new Gantry(across, origin, trolley, hook, hook.add(.5, 1, .5).subtract(chute),
                trolley.add(.5, QuarryConveyor.BELT_HEIGHT + QuarryConveyor.ITEM_CLEARANCE, .5).subtract(chute), quarry.across().getAxis() == Direction.Axis.X ? 0 : 90);
        }
    }

    /** Takes one part: {@code model} at {@code at} from the drive, turned {@code yaw} degrees about its centre. */
    interface Parts { void add(PartialModel model, Vec3 at, float yaw); }

    /** Lists every part that keeps its texture still, for this renderer and {@link QuarryVisual}; each places the scrolling belt and the spinning drill itself. */
    static void parts(CraneStructure.Result quarry, Gantry gantry, double depth, boolean drill, Parts parts) {
        for (int i = 0; i <= quarry.span(); i++)
            parts.add(i == 0 || i == quarry.span() ? END_TRUCK : GIRDER, gantry.origin().add(gantry.across().scale(i)), gantry.yaw());
        for (int i = -1; i <= quarry.span(); i++) parts.add(BRIDGE_BELT_FRAME, gantry.origin().add(gantry.across().scale(i)), gantry.yaw());
        parts.add(TROLLEY, gantry.trolley(), gantry.yaw());
        for (double y = -depth; y < 0; y += 1) if (Math.min(y + 1, 0) - y >= 0.05) parts.add(DRILL_STEM, gantry.trolley().add(0, y, 0), 0);
        if (drill) parts.add(HEAD_BIN, gantry.hook(), spoutYaw(quarry.across().getOpposite()));
        else parts.add(GRAPPLE, gantry.hook(), gantry.yaw());
        Vec3 bottom = gantry.chuteBottom();
        for (double y = bottom.y; y <= gantry.chuteTop().y; y += FOLD) {
            int fold = (int) Math.round((y - bottom.y) / FOLD);
            parts.add(fold % 2 == 0 ? CHUTE_RING : CHUTE_FOLD, new Vec3(bottom.x - .5, y - .5, bottom.z - .5), 0);
        }
    }

    /** The way the bridge belt's texture runs for the ore to travel towards the near rail. */
    static int beltDirection(CraneStructure.Result quarry) {
        return quarry.across().getAxis() == Direction.Axis.X ? -quarry.across().getStepX() : quarry.across().getStepZ();
    }

    static float headSpin(QuarryDriveBlockEntity be) { return be.working() ? AnimationTickHolder.getRenderTime(be.getLevel()) * 20 % 360 : 0; }

    /** The yaw that turns a model's +X towards {@code side}, for the bin's spout under the chute. */
    private static float spoutYaw(Direction side) {
        return switch (side) { case NORTH -> 90; case WEST -> 180; case SOUTH -> 270; default -> 0; };
    }
}
