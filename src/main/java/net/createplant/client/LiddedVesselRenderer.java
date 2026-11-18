package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.chemistry.ReactorModule;
import net.createplant.registry.CEReactor;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.createmod.catnip.render.FluidRenderHelper;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/**
 * The contents, the reaction-plant internals and the top of a lidded vessel, drawn once from its controller. The top is a fixed plate across the
 * opening of the top layer with a one-block hatch in its middle that swings up about its hinge edge; both keep the block-face pixel density by
 * taking each block's share of the texture.
 */
public class LiddedVesselRenderer extends SafeBlockEntityRenderer<LiddedVesselBlockEntity> {
    private static final float WALL = 2, MAX_ANGLE = 105;
    private static final Map<ReactorModule, PartialModel> INTERNALS = new EnumMap<>(ReactorModule.class);
    private final Map<LiddedVesselBlockEntity, Swirl> swirls = new WeakHashMap<>();
    private static final class Swirl { float time, angle; }
    private static final PartialModel BEARING = PartialModel.of(CreatePlant.location("block/lidded_vessel/shaft_bearing"));

    static {
        for (var entry : CEReactor.INTERNALS.entrySet()) INTERNALS.put(entry.getKey(), PartialModel.of(CreatePlant.location("block/lidded_vessel/internal/" + entry.getValue().getId().getPath() + "_wide")));
    }

    public static void init() { }

    public LiddedVesselRenderer(BlockEntityRendererProvider.Context context) { }

    @Override protected void renderSafe(LiddedVesselBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderShafts(be, ms, buffer, light);
        renderWindows(be, ms, buffer, light, overlay);
        if (!be.isController()) return;
        int width = be.getWidth(), height = be.getHeight();
        renderSludge(be, ms, buffer, light, overlay);
        if (width == 3) renderInternals(be, width, height, ms, buffer, light);
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite top = atlas.apply(CreatePlant.location("block/reactor/silver/top")), under = atlas.apply(CreatePlant.location("block/reactor/steel")),
            edge = atlas.apply(CreatePlant.location("block/lidded_vessel/internal/steel")), fitting = atlas.apply(CreatePlant.location("block/reactor/silver/frame"));
        TextureAtlasSprite joined = width == 3 ? PlantTankModels.VESSEL_CAP.getTarget() : width == 2 ? atlas.apply(CreatePlant.location("block/lidded_vessel/top_2")) : top;
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        float span = width * 16, middle = span / 2;
        if(width==1) {
            ms.pushPose();ms.translate(0,height-1,0);
            plate(vc,ms,WALL,WALL,16-WALL,16-WALL,16,top,under,light,overlay);
            ms.popPose();renderContents(be,width,height,ms,buffer,light,overlay);return;
        }
        box(vc, ms, middle - 2, -1, span - 7, middle + 2, 0, span - 3, fitting, fitting, edge, light, overlay);
        if (be.fouling().drainOpen()) box(vc, ms, middle - 1, -2, span - 8, middle + 1, -1, span - 2, edge, edge, fitting, light, overlay);
        else box(vc, ms, middle - 3, -2, span - 6, middle + 3, -1, span - 4, edge, edge, fitting, light, overlay);
        // Wide vessels keep a 22 px hatch in the middle of their joined top.
        float h0 = Math.max(WALL, middle - LiddedVesselBlock.HATCH_HALF), h1 = Math.min(span - WALL, middle + LiddedVesselBlock.HATCH_HALF);
        ms.pushPose();
        // Turn the vessel so that the hatch's hinge runs along its local north edge, on the top layer.
        ms.translate(width / 2f, height - 1, width / 2f);
        ms.mulPose(Axis.YP.rotationDegrees(180 - be.hinge().toYRot()));
        ms.translate(-width / 2f, 0, -width / 2f);
        if (width > 1) {
            squarePlate(vc, ms, span, h0, h1, joined, under, light, overlay);
            hole(vc, ms, h0, h1, edge, light, overlay);
        }
        box(vc, ms, middle - 2, 15, h0 - 1, middle + 2, 17, h0 + 1, fitting, fitting, fitting, light, overlay);
        ms.translate(0, 1, h0 / 16);
        ms.mulPose(Axis.XP.rotationDegrees(-MAX_ANGLE * be.lidOpening(partialTicks)));
        ms.translate(0, -1, -h0 / 16);
        float uv0 = width == 1 ? WALL : 0, uv1 = width == 1 ? 16 - WALL : 16;
        if (width == 3) capAcross(vc, ms, h0, h0, h1, h1, 16, joined, light, overlay);
        else up(vc, ms, h0, h0, h1, h1, 16, top, uv0, uv0, uv1, uv1, light, overlay);
        down(vc, ms, h0, h0, h1, h1, 14, under, uv0, uv0, uv1, uv1, light, overlay);
        for (int side = 0; side < 4; side++) side(vc, ms, side, h0, h0, h1, h1, edge, light, overlay);
        box(vc, ms, middle - 3, 16, h1 - 4, middle + 3, 17, h1 - 2, fitting, fitting, fitting, light, overlay);
        ms.popPose();
        renderContents(be, width, height, ms, buffer, light, overlay);
    }

    /** The fixed top round the hatch: four strips inside the walls. */
    private static void squarePlate(VertexConsumer vc, PoseStack ms, float span, float h0, float h1, TextureAtlasSprite top, TextureAtlasSprite under, int light, int overlay) {
        float a = span == 48 ? 0 : WALL, b = span - a;
        plate(vc, ms, a, a, b, h0, span, top, under, light, overlay);
        plate(vc, ms, a, h1, b, b, span, top, under, light, overlay);
        plate(vc, ms, a, h0, h0, h1, span, top, under, light, overlay);
        plate(vc, ms, h1, h0, b, h1, span, top, under, light, overlay);
    }

    /** The 2 px rim round the hatch's opening, seen when it is open. */
    private static void hole(VertexConsumer vc, PoseStack ms, float h0, float h1, TextureAtlasSprite edge, int light, int overlay) {
        quad(vc, ms, edge, h0, 16, h0, h0, 14, h0, h1, 14, h0, h1, 16, h0, 0, 0, h1 - h0, 2, 0, 0, 1, light, overlay);
        quad(vc, ms, edge, h1, 16, h1, h1, 14, h1, h0, 14, h1, h0, 16, h1, 0, 0, h1 - h0, 2, 0, 0, -1, light, overlay);
        quad(vc, ms, edge, h0, 16, h1, h0, 14, h1, h0, 14, h0, h0, 16, h0, 0, 0, h1 - h0, 2, 1, 0, 0, light, overlay);
        quad(vc, ms, edge, h1, 16, h0, h1, 14, h0, h1, 14, h1, h1, 16, h1, 0, 0, h1 - h0, 2, -1, 0, 0, light, overlay);
    }

    /** A 2 px plate from y 14 to 16, split at block edges so each part takes its own block's share of the texture. */
    private static void plate(VertexConsumer vc, PoseStack ms, float x0, float z0, float x1, float z1, float span, TextureAtlasSprite top, TextureAtlasSprite under, int light, int overlay) {
        if (x0 >= x1 || z0 >= z1) return;
        for (float x = x0; x < x1; ) {
            float xe = Math.min(x1, (float) Math.floor(x / 16 + 1) * 16);
            for (float z = z0; z < z1; ) {
                float ze = Math.min(z1, (float) Math.floor(z / 16 + 1) * 16), u = x % 16, v = z % 16;
                if (span == 48) cap(vc, ms, x, z, xe, ze, 16, top, light, overlay);
                else up(vc, ms, x, z, xe, ze, 16, top, x * 16 / span, z * 16 / span, xe * 16 / span, ze * 16 / span, light, overlay);
                down(vc, ms, x, z, xe, ze, 14, under, u, v, u + xe - x, v + ze - z, light, overlay);
                z = ze;
            }
            x = xe;
        }
    }

    static int capTextureIndex(int x, int z) {
        var context = new CTContext();
        context.left = x > 0;context.right = x < 2;context.up = z > 0;context.down = z < 2;
        return PlantTankModels.VESSEL_CAP.getType().getTextureIndex(context);
    }

    /** A 3x3 cap face over several blocks, split at their edges so each part takes its own block's tile of the connected texture. */
    private static void capAcross(VertexConsumer vc, PoseStack ms, float x0, float z0, float x1, float z1, float y, TextureAtlasSprite sprite, int light, int overlay) {
        for (float x = x0; x < x1; ) {
            float xe = Math.min(x1, (float) Math.floor(x / 16 + 1) * 16);
            for (float z = z0; z < z1; ) {
                float ze = Math.min(z1, (float) Math.floor(z / 16 + 1) * 16);
                cap(vc, ms, x, z, xe, ze, y, sprite, light, overlay);
                z = ze;
            }
            x = xe;
        }
    }

    private static void cap(VertexConsumer vc, PoseStack ms, float x0, float z0, float x1, float z1, float y, TextureAtlasSprite sprite, int light, int overlay) {
        int x = (int) x0 / 16, z = (int) z0 / 16, index = capTextureIndex(x, z);
        float u = index % 4 * 4, v = index / 4 * 4;
        up(vc, ms, x0, z0, x1, z1, y, sprite, u + (x0 - x * 16) / 4, v + (z0 - z * 16) / 4,
            u + (x1 - x * 16) / 4, v + (z1 - z * 16) / 4, light, overlay);
    }

    private static void renderShafts(LiddedVesselBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        var state = be.getBlockState();
        var block = (LiddedVesselBlock) state.getBlock();
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        for (Direction face : Direction.values()) {
            if (!block.hasShaftTowards(be.getLevel(), be.getBlockPos(), state, face)) continue;
            CachedBuffers.partialFacing(BEARING, state, face).light(light).renderInto(ms, vc);
            ms.pushPose();
            var adjacentPos = be.getBlockPos().relative(face);
            var adjacent = be.getLevel().getBlockState(adjacentPos);
            if (!(adjacent.getBlock() instanceof IRotate kinetic && kinetic.hasShaftTowards(be.getLevel(), adjacentPos, adjacent, face.getOpposite())))
                ms.translate(face.getStepX() / 8f, face.getStepY() / 8f, face.getStepZ() / 8f);
            KineticBlockEntityRenderer.renderRotatingBuffer(be, CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, face), ms, vc, light);
            ms.popPose();
        }
    }

    /** Every layer shares the actual receiving shaft's rotation. */
    private static void renderInternals(LiddedVesselBlockEntity be, int width, int height, PoseStack ms, MultiBufferSource buffer, int light) {
        var level = be.getLevel();
        float angle = AnimationTickHolder.getRenderTime(level) * be.rotationSpeed() * .3f % 360 * (float) Math.PI / 180;
        float centre = width / 2f;
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        for (int y = 0; y < height; y++) {
            var slot = be.slot(y);
            ReactorModule module = slot == null ? null : CEReactor.module(slot.internal());
            if (module == null || width!=3) continue;
            ms.pushPose();
            ms.translate(centre - .5f, y, centre - .5f);
            PartialModel support = INTERNALS.get(module);
            var model = CachedBuffers.partial(support, be.getBlockState());
            (module == ReactorModule.STIRRED ? model.rotateCentered(angle, Direction.UP) : model).light(light).renderInto(ms, vc);
            ms.popPose();
        }
    }

    private void renderContents(LiddedVesselBlockEntity be, int width, int height, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        long amount = 0;
        int gas = -1;
        for (int i = 0; i < be.displayedFluidSlots(); i++) {
            amount += be.displayedLiquid(i).getAmount();
            if (gas < 0 && !be.displayedGas(i).isEmpty()) gas = i;
        }
        float inset = WALL / 16, surface = inset;
        float depth = Math.min(1, (float) amount / be.getDisplayedCapacity()) * (height - 2 * inset);
        renderItems(be, inset + depth, ms, buffer, light, overlay);
        var liquidBuffer = FluidRenderHelper.getFluidBuilder(buffer);
        for (int i = 0; i < be.displayedFluidSlots() && amount > 0; i++) {
            var fluid = be.displayedLiquid(i);
            if (fluid.isEmpty()) continue;
            float top = surface + depth * fluid.getAmount() / amount;
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, inset, surface, inset, width - inset, top, width - inset, liquidBuffer, ms, light, false, true);
            surface = top;
        }
        if (gas >= 0 && surface < height - inset) {
            var vapour = new VertexConsumerWrapper(liquidBuffer) {
                @Override public VertexConsumer setColor(int r, int g, int b, int a) { parent.setColor(r, g, b, a / 9);return this; }
            };
            NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(be.displayedGas(gas), inset, surface, inset, width - inset, height - inset, width - inset, vapour, ms, light, false, true);
        }
    }

    private static void renderSludge(LiddedVesselBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        float dirt = be.fouling().foulingFraction();
        if (dirt <= 0) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(CreatePlant.location("block/lidded_vessel/sludge"));
        var vc = buffer.getBuffer(RenderType.solid());
        float y = WALL + .05f + dirt * 1.4f;
        for (int z = 2; z < be.getWidth() * 16 - 2; ) {
            int low = 2, high = be.getWidth() * 16 - 2;
            int end = Math.min(high, (z / 16 + 1) * 16);
            for (int x = low; x < high; ) {
                int next = Math.min(high, (x / 16 + 1) * 16);
                up(vc, ms, x, z, next, end, y, sprite, x % 16, z % 16, x % 16 + next - x, z % 16 + end - z, light, overlay);
                x = next;
            }
            z = end;
        }
    }

    private void renderItems(LiddedVesselBlockEntity be, float surface, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        int count = 0;
        for (int i = 0; i < be.displayedItemSlots(); i++) if (!be.displayedItem(i).isEmpty()) count++;
        if (count == 0) return;
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        Swirl swirl = swirls.computeIfAbsent(be, ignored -> new Swirl());
        if (be.mixing()) swirl.angle += Math.clamp(time - swirl.time, 0, 2) * Math.clamp(be.rotationSpeed() / 32, -4, 4) * .04f;
        swirl.time = time;
        float centre = be.getWidth() / 2f, radius = Math.min((be.getWidth() - .8f) / 2, 2), y = Math.min(be.getHeight() - .3f, Math.max(.24f, surface + .06f));
        var renderer = Minecraft.getInstance().getItemRenderer();
        int index = 0;
        for (int i = 0; i < be.displayedItemSlots(); i++) {
            var stack = be.displayedItem(i);
            if (stack.isEmpty()) continue;
            float angle = index * 2.399963f + swirl.angle, distance = radius * (float) Math.sqrt((index + .5f) / count);
            float bob = surface > WALL / 16 ? (float) Math.sin(time * .09f + index * 1.7f) * .015f : 0;
            ms.pushPose();
            ms.translate(centre + Math.cos(angle) * distance, y + bob, centre + Math.sin(angle) * distance);
            ms.mulPose(Axis.YP.rotation(angle));
            if (!renderer.getModel(stack, be.getLevel(), null, i).isGui3d()) ms.mulPose(Axis.XP.rotationDegrees(90));
            ms.scale(.65f, .65f, .65f);
            renderer.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, ms, buffer, be.getLevel(), i);
            ms.popPose();
            index++;
        }
    }

    private static void renderWindows(LiddedVesselBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var openings = LiddedVesselWindowModel.profile(be).openings();
        if (openings.isEmpty()) return;
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var owner = be.getControllerBE();
        var glass = atlas.apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", owner != null && owner.getHeight() == 1 ? "block/fluid_tank_window_single" : "block/fluid_tank_window"));
        var frost = atlas.apply(CreatePlant.location("block/lidded_vessel/window_frost"));
        var trim = atlas.apply(CreatePlant.location("block/lidded_vessel/internal/steel"));
        var metal = buffer.getBuffer(RenderType.solid());
        float ice = owner == null ? 0 : owner.frostLevel();
        for (var opening : openings) {
            AABB b = opening.box();
            if (b.minY > 0) face(metal, ms, new AABB(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ), Direction.UP, trim, light, overlay);
            if (b.maxY < 1) face(metal, ms, new AABB(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ), Direction.DOWN, trim, light, overlay);
            if (opening.face().getAxis() == Direction.Axis.Z) {
                if (b.minX > 0) face(metal, ms, new AABB(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.maxZ), Direction.EAST, trim, light, overlay);
                if (b.maxX < 1) face(metal, ms, new AABB(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ), Direction.WEST, trim, light, overlay);
            } else {
                if (b.minZ > 0) face(metal, ms, new AABB(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ), Direction.SOUTH, trim, light, overlay);
                if (b.maxZ < 1) face(metal, ms, new AABB(b.minX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ), Direction.NORTH, trim, light, overlay);
            }
        }
        var pane = buffer.getBuffer(RenderType.cutoutMipped());
        for (var opening : openings) {
            AABB b = opening.box();
            double plane = opening.face().getAxis() == Direction.Axis.X ? (b.minX + b.maxX) / 2 : (b.minZ + b.maxZ) / 2;
            AABB flat = opening.face().getAxis() == Direction.Axis.X ? new AABB(plane, b.minY, b.minZ, plane, b.maxY, b.maxZ)
                : new AABB(b.minX, b.minY, plane, b.maxX, b.maxY, plane);
            tankWindow(be, pane, ms, flat, opening.face(), glass, light, overlay);
        }
        if (ice <= 0) return;
        var frozen = new VertexConsumerWrapper(FluidRenderHelper.getFluidBuilder(buffer)) {
            @Override public VertexConsumer setColor(int r, int g, int b, int a) { parent.setColor(r, g, b, (int) (a * ice));return this; }
        };
        for (var opening : openings) {
            AABB b = opening.box();
            double plane = opening.face().getAxis() == Direction.Axis.X ? (b.minX + b.maxX) / 2 : (b.minZ + b.maxZ) / 2;
            AABB flat = opening.face().getAxis() == Direction.Axis.X ? new AABB(plane, b.minY, b.minZ, plane, b.maxY, b.maxZ)
                : new AABB(b.minX, b.minY, plane, b.maxX, b.maxY, plane);
            var offset = opening.face();
            var outer = flat.move(offset.getStepX() * .0005, 0, offset.getStepZ() * .0005);
            var inner = flat.move(-offset.getStepX() * .0005, 0, -offset.getStepZ() * .0005);
            face(frozen, ms, outer, offset, frost, light, overlay);
            face(frozen, ms, inner, offset.getOpposite(), frost, light, overlay);
        }
    }

    private static void tankWindow(LiddedVesselBlockEntity be, VertexConsumer vc, PoseStack ms, AABB b, Direction face, TextureAtlasSprite sprite, int light, int overlay) {
        var owner = be.getControllerBE();
        if (owner == null) return;
        int layer = be.getBlockPos().getY() - owner.getBlockPos().getY();
        int column = face.getAxis() == Direction.Axis.X ? be.getBlockPos().getZ() - owner.getBlockPos().getZ() : be.getBlockPos().getX() - owner.getBlockPos().getX();
        float low = (float) (face.getAxis() == Direction.Axis.X ? b.minZ : b.minX) * 16;
        float high = (float) (face.getAxis() == Direction.Axis.X ? b.maxZ : b.maxX) * 16;
        float u0 = face == Direction.NORTH || face == Direction.EAST ? owner.getWidth() * 8 + 4 - column * 16 - high : column * 16 + low - (owner.getWidth() * 8 - 4);
        if (layer > 0 && layer < owner.getHeight() - 1) u0 += 8;
        float u1 = u0 + high - low;
        float v0 = owner.getHeight() == 1 ? 0 : layer == 0 ? 4 : 0;
        float v1 = owner.getHeight() == 1 ? 8 : layer == owner.getHeight() - 1 ? 12 : 16;
        windowQuad(vc, ms, b, face, sprite, u0, v0, u1, v1, light, overlay);
        windowQuad(vc, ms, b, face.getOpposite(), sprite, u0, v0, u1, v1, light, overlay);
    }

    private static void windowQuad(VertexConsumer vc, PoseStack ms, AABB b, Direction face, TextureAtlasSprite sprite, float u0, float v0, float u1, float v1, int light, int overlay) {
        float x = (float) b.minX * 16, y = (float) b.minY * 16, z = (float) b.minZ * 16, X = (float) b.maxX * 16, Y = (float) b.maxY * 16, Z = (float) b.maxZ * 16;
        switch (face) {
            case NORTH -> quad(vc, ms, sprite, X, Y, z, X, y, z, x, y, z, x, Y, z, u0, v0, u1, v1, 0, 0, -1, light, overlay);
            case SOUTH -> quad(vc, ms, sprite, x, Y, Z, x, y, Z, X, y, Z, X, Y, Z, u0, v0, u1, v1, 0, 0, 1, light, overlay);
            case WEST -> quad(vc, ms, sprite, x, Y, z, x, y, z, x, y, Z, x, Y, Z, u0, v0, u1, v1, -1, 0, 0, light, overlay);
            case EAST -> quad(vc, ms, sprite, X, Y, Z, X, y, Z, X, y, z, X, Y, z, u0, v0, u1, v1, 1, 0, 0, light, overlay);
            default -> throw new IllegalArgumentException("A tank window must face a side");
        }
    }

    private static void face(VertexConsumer vc, PoseStack ms, AABB b, Direction face, TextureAtlasSprite sprite, int light, int overlay) {
        float x = (float) b.minX * 16, y = (float) b.minY * 16, z = (float) b.minZ * 16, X = (float) b.maxX * 16, Y = (float) b.maxY * 16, Z = (float) b.maxZ * 16;
        switch (face) {
            case UP -> up(vc, ms, x, z, X, Z, Y, sprite, x, z, X, Z, light, overlay);
            case DOWN -> down(vc, ms, x, z, X, Z, y, sprite, x, z, X, Z, light, overlay);
            case NORTH -> quad(vc, ms, sprite, X, Y, z, X, y, z, x, y, z, x, Y, z, x, 16 - Y, X, 16 - y, 0, 0, -1, light, overlay);
            case SOUTH -> quad(vc, ms, sprite, x, Y, Z, x, y, Z, X, y, Z, X, Y, Z, x, 16 - Y, X, 16 - y, 0, 0, 1, light, overlay);
            case WEST -> quad(vc, ms, sprite, x, Y, z, x, y, z, x, y, Z, x, Y, Z, z, 16 - Y, Z, 16 - y, -1, 0, 0, light, overlay);
            case EAST -> quad(vc, ms, sprite, X, Y, Z, X, y, Z, X, y, z, X, Y, z, z, 16 - Y, Z, 16 - y, 1, 0, 0, light, overlay);
        }
    }

    private static void box(VertexConsumer vc, PoseStack ms, float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite top, TextureAtlasSprite bottom,
        TextureAtlasSprite side, int light, int overlay) {
        up(vc, ms, x0, z0, x1, z1, y1, top, 0, 0, x1 - x0, z1 - z0, light, overlay);
        down(vc, ms, x0, z0, x1, z1, y0, bottom, 0, 0, x1 - x0, z1 - z0, light, overlay);
        quad(vc, ms, side, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, x1 - x0, y1 - y0, 0, 0, -1, light, overlay);
        quad(vc, ms, side, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, x1 - x0, y1 - y0, 0, 0, 1, light, overlay);
        quad(vc, ms, side, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, 0, 0, z1 - z0, y1 - y0, -1, 0, 0, light, overlay);
        quad(vc, ms, side, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 0, 0, z1 - z0, y1 - y0, 1, 0, 0, light, overlay);
    }

    /** The hatch's 2 px edge on the given side, 0 to 3 for north, south, west and east. */
    private static void side(VertexConsumer vc, PoseStack ms, int side, float x0, float z0, float x1, float z1, TextureAtlasSprite edge, int light, int overlay) {
        switch (side) {
            case 0 -> quad(vc, ms, edge, x1, 16, z0, x1, 14, z0, x0, 14, z0, x0, 16, z0, 0, 0, x1 - x0, 2, 0, 0, -1, light, overlay);
            case 1 -> quad(vc, ms, edge, x0, 16, z1, x0, 14, z1, x1, 14, z1, x1, 16, z1, 0, 0, x1 - x0, 2, 0, 0, 1, light, overlay);
            case 2 -> quad(vc, ms, edge, x0, 16, z0, x0, 14, z0, x0, 14, z1, x0, 16, z1, 0, 0, z1 - z0, 2, -1, 0, 0, light, overlay);
            default -> quad(vc, ms, edge, x1, 16, z1, x1, 14, z1, x1, 14, z0, x1, 16, z0, 0, 0, z1 - z0, 2, 1, 0, 0, light, overlay);
        }
    }

    private static void up(VertexConsumer vc, PoseStack ms, float x0, float z0, float x1, float z1, float y, TextureAtlasSprite sprite, float u0, float v0, float u1, float v1, int light, int overlay) {
        quad(vc, ms, sprite, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, u0, v0, u1, v1, 0, 1, 0, light, overlay);
    }

    private static void down(VertexConsumer vc, PoseStack ms, float x0, float z0, float x1, float z1, float y, TextureAtlasSprite sprite, float u0, float v0, float u1, float v1, int light, int overlay) {
        quad(vc, ms, sprite, x0, y, z1, x0, y, z0, x1, y, z0, x1, y, z1, u0, v1, u1, v0, 0, -1, 0, light, overlay);
    }

    /** Corners in pixels, in vanilla's face order; the texture runs u0 to u1 from the first corner pair to the last, v0 to v1 top to bottom. */
    private static void quad(VertexConsumer vc, PoseStack ms, TextureAtlasSprite sprite, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
        float dx, float dy, float dz, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light, int overlay) {
        var pose = ms.last();
        float su0 = sprite.getU(u0 / 16), su1 = sprite.getU(u1 / 16), sv0 = sprite.getV(v0 / 16), sv1 = sprite.getV(v1 / 16);
        vertex(vc, pose, ax, ay, az, su0, sv0, nx, ny, nz, light, overlay);
        vertex(vc, pose, bx, by, bz, su0, sv1, nx, ny, nz, light, overlay);
        vertex(vc, pose, cx, cy, cz, su1, sv1, nx, ny, nz, light, overlay);
        vertex(vc, pose, dx, dy, dz, su1, sv0, nx, ny, nz, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(pose, x / 16, y / 16, z / 16).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
