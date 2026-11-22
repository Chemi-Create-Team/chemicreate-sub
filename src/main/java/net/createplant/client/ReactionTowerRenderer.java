package net.createplant.client;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.CreatePlant;
import net.createplant.block.ReactionTowerBlock;
import net.createplant.block.entity.ReactionTowerBlockEntity;
import net.createplant.thermal.FluidMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * Drawn once from a reaction tower's bottom centre, seen through its sight glasses: the reaction trays inside it with the catalyst, solid feed and
 * products lying on them, or a separator's liquid and vapour; and, on the front of the shell, Create's boiler gauge in the tower's steel: a half-size
 * thermometer on the left of every layer that holds a tray, and on
 * the second layer from the bottom, on the right, the loop pressure gauge with a red needle. As on a boiler, a gauge is left off where a block stands
 * in front of it.
 */
public class ReactionTowerRenderer extends SafeBlockEntityRenderer<ReactionTowerBlockEntity> {
    /** The reaction tray drawn for a 3x3 stainless tank, narrowed to the tower's thicker walls. */
    private static final PartialModel TRAY = PartialModel.of(CreatePlant.location("block/lidded_vessel/internal/reactor_separator_core_wide"));
    private static final float INSET = ReactionTowerBlock.WALL / 16f, FLOOR = ReactionTowerBlock.FLOOR / 16f, ROOF = ReactionTowerBlock.ROOF / 16f, TRAY_SCALE = (48 - 2 * ReactionTowerBlock.WALL) / 44f;
    /**
     * Where items lie on a tray: the top of its lower plate in the layer, how thick each item in a heap lies, and the places across it, the middle one
     * kept for the catalyst, then the four in line with the sight glasses before the corners.
     */
    private static final float TRAY_TOP = 4 / 16f, PILE = 1 / 32f, ITEM_SCALE = .9f;
    private static final float[][] PLACES = {{0, 0}, {0, -.62f}, {0, .62f}, {-.62f, 0}, {.62f, 0}, {-.62f, -.62f}, {.62f, .62f}, {.62f, -.62f}, {-.62f, .62f}};

    /** Where Create pivots the boiler gauge's dial, and how far it swings over the full scale. */
    private static final float DIAL_PIVOT_Y = 6 / 16f, DIAL_PIVOT_Z = 8 / 16f, DIAL_SWEEP = 145;
    /** Distance from the tower's axis to the gauge's place on the shell, as Create sets a boiler gauge on a tank of the same width. */
    private static final float GAUGE_OUT = 1.5f - 6 / 16f, THERMOMETER_SCALE = .5f;
    /** Blocks along the front from its middle: the side's clockwise neighbour is on the viewer's left. */
    private static final int LEFT = 1, RIGHT = -1;
    private static final PartialModel GAUGE = PartialModel.of(CreatePlant.location("block/reaction_tower/gauge")),
        GAUGE_DIAL = PartialModel.of(CreatePlant.location("block/reaction_tower/gauge_dial"));
    private static final int PRESSURE_NEEDLE = 0xFF6A5A;
    private static final PartialModel HATCH = PartialModel.of(CreatePlant.location("block/reaction_tower/hatch")),
        LADDER = PartialModel.of(CreatePlant.location("block/lidded_vessel/ladder"));
    private static final float HATCH_HINGE = (ReactionTowerBlock.HATCH_MIN - 16) / 16f, HATCH_ANGLE = 105;

    public static void init() { }

    public ReactionTowerRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(ReactionTowerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.owner() != be) return;
        renderGauges(be, ms, buffer, light);
        renderHatch(be, partialTicks, ms, buffer);
        renderLadder(be, ms, buffer);
        // The inside is lit through the sight glasses, not by the shut-in bottom block.
        if (!be.hasTrays()) { renderContents(be, ms, buffer, LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().north(2)));return; }
        VertexConsumer vc = buffer.getBuffer(RenderType.cutoutMipped());
        for (int y = 0; y < be.layers(); y++) {
            var layer = be.layer(y);
            if (layer == null || !layer.hasTray()) continue;
            int inside = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(y).relative(be.front(), 2));
            ms.pushPose();
            ms.translate(.5f, y, .5f);
            ms.scale(TRAY_SCALE, 1, TRAY_SCALE);
            ms.translate(-.5f, 0, -.5f);
            CachedBuffers.partial(TRAY, be.getBlockState()).light(inside).renderInto(ms, vc);
            ms.popPose();
            renderItems(layer, y, ms, buffer, inside, overlay);
        }
    }

    /**
     * The catalyst in the middle of a tray, and the solid feed and products lying round it in small heaps: one item laid flat for every two held,
     * at most eight of each, piled on the places in turn.
     */
    private static void renderItems(ReactionTowerBlockEntity layer, int y, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var items = Minecraft.getInstance().getItemRenderer();
        if (!layer.catalystStack().isEmpty()) lay(items, layer, layer.catalystStack(), PLACES[0], y, 0, 0, ms, buffer, light, overlay);
        int first = 1;
        for (var stack : List.of(layer.load(), layer.made())) {
            if (stack.isEmpty()) continue;
            int shown = Math.min(8, (stack.getCount() + 1) / 2);
            for (int i = 0; i < shown; i++) lay(items, layer, stack, PLACES[first + i % 4], y, i / 4, first + i, ms, buffer, light, overlay);
            first += 4;
        }
    }

    private static void lay(ItemRenderer items, ReactionTowerBlockEntity layer, ItemStack stack, float[] at, int y, int level, int seed, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ms.pushPose();
        ms.translate(.5f + at[0], y + TRAY_TOP + PILE * (level + .5f), .5f + at[1]);
        ms.mulPose(Axis.YP.rotationDegrees(seed * 47));
        ms.mulPose(Axis.XP.rotationDegrees(90));
        ms.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, layer.getLevel(), seed);
        ms.popPose();
    }

    private static void renderHatch(ReactionTowerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer) {
        int top = be.layers() - 1;
        ms.pushPose();
        ms.translate(.5f, top + 1, .5f);
        ms.mulPose(Axis.YP.rotationDegrees(180 - be.front().getOpposite().toYRot()));
        ms.translate(-.5f, 0, HATCH_HINGE - .5f);
        ms.mulPose(Axis.XP.rotationDegrees(-HATCH_ANGLE * be.lidOpening(partialTicks)));
        ms.translate(0, -1, -HATCH_HINGE);
        CachedBuffers.partial(HATCH, be.getBlockState()).light(LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(top + 1))).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();
    }

    private static void renderLadder(ReactionTowerBlockEntity be, PoseStack ms, MultiBufferSource buffer) {
        Direction side = be.ladderSide();
        BlockPos base = be.ladderBase();
        var vc = buffer.getBuffer(RenderType.solid());
        ms.pushPose();
        ms.translate(base.getX() - be.getBlockPos().getX() + .5f, 0, base.getZ() - be.getBlockPos().getZ() + .5f);
        ms.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
        ms.translate(-.5f, 0, -.5f);
        for (int y = 0; y < be.layers(); y++) {
            int light = LevelRenderer.getLightColor(be.getLevel(), base.above(y).relative(side));
            CachedBuffers.partial(LADDER, be.getBlockState()).light(light).renderInto(ms, vc);
            ms.translate(0, 1, 0);
        }
        ms.popPose();
    }

    private static void renderGauges(ReactionTowerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        int layers = be.layers();
        for (int y = 0; y < layers; y++) {
            var layer = be.layer(y);
            if (layer != null && layer.hasTray()) gauge(be, ms, vc, y, LEFT, THERMOMETER_SCALE, be.layerTemperature(y) / ReactionTowerBlockEntity.MAX_TEMPERATURE, -1, light);
        }
        if (layers >= 2) gauge(be, ms, vc, 1, RIGHT, 1, be.pressure() / ReactionTowerBlockEntity.MAX_PRESSURE, PRESSURE_NEEDLE, light);
    }

    /** One gauge on the front of a layer, moved along the side by whole blocks and scaled about the middle of its mount; a needle tint of -1 keeps its colours. */
    private static void gauge(ReactionTowerBlockEntity be, PoseStack ms, VertexConsumer vc, int y, int along, float scale, float fraction, int needle, int light) {
        var state = be.getBlockState();
        Direction side = be.front();
        if (!be.getLevel().getBlockState(be.getBlockPos().above(y).relative(side, 2).relative(side.getClockWise(), along)).canBeReplaced()) return;
        float yRot = -side.toYRot() - 90, angle = -DIAL_SWEEP * Math.clamp(fraction, 0, 1) + 90;
        ms.pushPose();
        ms.translate(.5f, y + .5f, .5f);
        CachedBuffers.partial(GAUGE, state).rotateYDegrees(yRot).uncenter().translate(GAUGE_OUT, 0, along).translate(1, .5f, .5f).scale(scale).translate(-1, -.5f, -.5f)
            .light(light).renderInto(ms, vc);
        var dial = CachedBuffers.partial(GAUGE_DIAL, state);
        if (needle != -1) dial.color(needle);
        dial.rotateYDegrees(yRot).uncenter().translate(GAUGE_OUT, 0, along).translate(1, .5f, .5f).scale(scale).translate(-1, -.5f, -.5f).translate(0, DIAL_PIVOT_Y, DIAL_PIVOT_Z).rotateXDegrees(angle).translate(0, -DIAL_PIVOT_Y, -DIAL_PIVOT_Z).light(light).renderInto(ms, vc);
        ms.popPose();
    }

    /** The liquids settle in layers on the floor; the first gas fills the space above them as a faint haze. */
    private static void renderContents(ReactionTowerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        float x0 = -1 + INSET, x1 = 2 - INSET, top = be.layers() - ROOF;
        long liquid = 0;
        int gas = -1;
        for (int i = 0; i < 4; i++) {
            var fluid = be.tank(i);
            if (fluid.isEmpty()) continue;
            if (net.createplant.thermal.FluidState.gas(fluid)) { if (gas < 0) gas = i; }
            else liquid += fluid.getAmount();
        }
        var builder = FluidRenderHelper.getFluidBuilder(buffer);
        float surface = FLOOR, depth = Math.min(1, (float) liquid / (ReactionTowerBlockEntity.VOLUME_PER_LAYER * be.layers())) * (top - FLOOR);
        for (int i = 0; i < 4 && liquid > 0; i++) {
            var fluid = be.tank(i);
            if (fluid.isEmpty() || net.createplant.thermal.FluidState.gas(fluid)) continue;
            float next = surface + depth * fluid.getAmount() / liquid;
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, x0, surface, x0, x1, next, x1, builder, ms, light, false, true);
            surface = next;
        }
        if (gas >= 0 && surface < top) {
            var vapour = new VertexConsumerWrapper(builder) {
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { parent.setColor(r, g, b, a / 9);return this; }
            };
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(be.tank(gas), x0, surface, x0, x1, top, x1, vapour, ms, light, false, true);
        }
    }
}
