package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createplant.CreatePlant;
import net.createplant.block.entity.CraneDriveBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the moving parts of a service gantry crane from its drive: the bridge girder across both rails with an end truck on
 * each, the trolley on the girder, the handling mast, and the grapple with what it carries.
 */
public class CraneRenderer extends KineticBlockEntityRenderer<CraneDriveBlockEntity> {
    public static final PartialModel GIRDER = PartialModel.of(CreatePlant.location("block/crane/girder"));
    public static final PartialModel END_TRUCK = PartialModel.of(CreatePlant.location("block/crane/end_truck"));
    public static final PartialModel TROLLEY = PartialModel.of(CreatePlant.location("block/crane/trolley"));
    public static final PartialModel MAST = PartialModel.of(CreatePlant.location("block/crane/mast_segment"));
    public static final PartialModel LOWER_MAST = PartialModel.of(CreatePlant.location("block/crane/mast_lower_segment"));
    public static final PartialModel MAST_HEAD = PartialModel.of(CreatePlant.location("block/crane/mast_head"));
    private static final PartialModel BLANKET_LOAD = PartialModel.of(CreatePlant.location("block/plant_systems/blanket_load"));
    private static final PartialModel DIVERTOR_LOAD = PartialModel.of(CreatePlant.location("block/plant_systems/divertor_load"));
    private static final PartialModel DIVERTOR_HALF = PartialModel.of(CreatePlant.location("block/plant_systems/divertor_load_half"));
    private static final PartialModel DIVERTOR_LEFT = PartialModel.of(CreatePlant.location("block/plant_systems/divertor_load_left"));
    private static final PartialModel DIVERTOR_RIGHT = PartialModel.of(CreatePlant.location("block/plant_systems/divertor_load_right"));

    public CraneRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    public static void init() { }

    @Override protected BlockState getRenderedBlockState(CraneDriveBlockEntity be) { return shaft(be.getBlockState().getValue(BlockStateProperties.AXIS)); }

    @Override public boolean shouldRenderOffScreen(CraneDriveBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 128; }

    @Override protected void renderSafe(CraneDriveBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        var crane = be.structure();
        if (crane == null || be.getLevel() == null) return;
        BlockState state = be.getBlockState();
        VertexConsumer solid = buffer.getBuffer(RenderType.cutout());
        double u = be.renderedU(partialTicks), v = be.renderedV(partialTicks);
        double depth = !be.fusion() ? CraneDriveBlockEntity.SERVICE_MAST_DEPTH : be.renderedDepth(partialTicks);
        Vec3 along = Vec3.atLowerCornerOf(crane.along().getNormal()), across = Vec3.atLowerCornerOf(crane.across().getNormal());
        Vec3 origin = Vec3.atLowerCornerOf(crane.start().subtract(be.getBlockPos())).add(along.scale(u)).add(0, 1, 0);
        // The girder model runs along X; turn it to lie across the rails.
        float yaw = crane.across().getAxis() == Direction.Axis.X ? 0 : 90;
        for (int i = 0; i <= crane.span(); i++)
            draw(CachedBuffers.partial(i == 0 || i == crane.span() ? END_TRUCK : GIRDER, state), origin.add(across.scale(i)), yaw, be, ms, solid);
        Vec3 trolley = origin.add(across.scale(v));
        draw(CachedBuffers.partial(TROLLEY, state), trolley, yaw, be, ms, solid);
        // Reactor service keeps a fixed ten-block wide section and five-block narrow section while the bridge moves horizontally.
        double hookY = -1 - depth;
        int mastSegment = 0;
        for (double y = hookY + 1; y < 0; y += 1) {
            double top = Math.min(y + 1, 0);
            if (top - y < 0.05) continue;
            draw(CachedBuffers.partial(mastSegment < CraneDriveBlockEntity.LOWER_MAST_LENGTH - 1 ? LOWER_MAST : MAST, state), trolley.add(0, y, 0), 0, be, ms, solid);
            mastSegment++;
        }
        Vec3 hook = trolley.add(0, hookY, 0);
        draw(CachedBuffers.partial(MAST_HEAD, state), hook, yaw, be, ms, solid);
        if (be.fusion() && be.working()) {
            if (be.fusionComponent() == 0) draw(CachedBuffers.partial(BLANKET_LOAD, state), hook.add(0, -1.5, 0), yaw, be, ms, solid);
            else {
                for (int part = 0; part < 4; part++) draw(CachedBuffers.partial(part == 3 ? DIVERTOR_HALF : DIVERTOR_LOAD, state), hook.add(part - 1.75, -2.1, 0), yaw, be, ms, solid);
                draw(CachedBuffers.partial(DIVERTOR_LEFT, state), hook.add(-1.75, -1.85, 0), yaw, be, ms, solid);
                draw(CachedBuffers.partial(DIVERTOR_RIGHT, state), hook.add(1, -1.85, 0), yaw, be, ms, solid);
            }
        }
        // A fuel rod held in the crane above the grapple, going down through the standpipe into its hole or the pool, or up out of it.
        if (be.carried() != 0) for (int i = 0; i < be.carriedLength(); i++)
            draw(CachedBuffers.partial(FuelChannelRenderer.TUBE, state), hook.add(0, i - be.slide(), 0), 0, be, ms, solid);
    }

    static void draw(SuperByteBuffer model, Vec3 at, float yaw, BlockEntity be, PoseStack ms, VertexConsumer consumer) {
        BlockPos lit = be.getBlockPos().offset((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        model.translate(at.x, at.y, at.z).center().rotateYDegrees(yaw).uncenter()
            .light(LevelRenderer.getLightColor(be.getLevel(), lit)).renderInto(ms, consumer);
    }
}
