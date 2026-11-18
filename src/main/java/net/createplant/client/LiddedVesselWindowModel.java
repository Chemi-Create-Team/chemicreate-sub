package net.createplant.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.createplant.CreatePlant;
import net.createplant.block.LiddedVesselBlock;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Cuts inspection windows and joins three-wide end plates without changing the sealed collision shape. */
public class LiddedVesselWindowModel extends BakedModelWrapperWithData {
    private static final ModelProperty<Profile> WINDOWS = new ModelProperty<>();
    private static final Map<LiddedVesselBlockEntity, Profile> PROFILES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Profile EMPTY = new Profile(null, List.of());
    private static final float EPSILON = 1e-5f;
    private static final ResourceLocation RIM = CreatePlant.location("block/lidded_vessel/internal/steel"), BOTTOM = CreatePlant.location("block/reactor/silver/top");

    public LiddedVesselWindowModel(BakedModel model) { super(model); }

    public record Opening(Direction face, AABB box) { }
    private record Shape(BlockState state, int width, int height, int x, int y, int z) { }
    public record Profile(Shape shape, List<Opening> openings) { }

    public static Profile profile(LiddedVesselBlockEntity be) {
        var owner = be.getControllerBE();
        if (!be.windowsEnabled() || owner == null || be.getLevel() == null) return EMPTY;
        int x = be.getBlockPos().getX() - owner.getBlockPos().getX(), y = be.getBlockPos().getY() - owner.getBlockPos().getY(), z = be.getBlockPos().getZ() - owner.getBlockPos().getZ();
        var previous = PROFILES.get(be);
        if (previous != null) {
            var shape = previous.shape();
            if (shape.state() == be.getBlockState() && shape.width() == owner.getWidth() && shape.height() == owner.getHeight() && shape.x() == x && shape.y() == y && shape.z() == z) return previous;
        }
        var shape = new Shape(be.getBlockState(), owner.getWidth(), owner.getHeight(), x, y, z);
        var openings = new ArrayList<Opening>();
        var block = (LiddedVesselBlock) shape.state().getBlock();
        for (Direction face : Direction.Plane.HORIZONTAL) {
            if (!shape.state().getValue(LiddedVesselBlock.wall(face)) || block.hasShaftTowards(be.getLevel(), be.getBlockPos(), shape.state(), face)) continue;
            int along = face.getAxis() == Direction.Axis.X ? shape.z() : shape.x();
            double low = Math.max(0, shape.width() * 8 - 4 - along * 16), high = Math.min(16, shape.width() * 8 + 4 - along * 16);
            if (high <= low) continue;
            double bottom = shape.y() == 0 ? 4 : 0, top = shape.y() == shape.height() - 1 ? 12 : 16;
            AABB box = switch (face) {
                case NORTH -> new AABB(low / 16, bottom / 16, 0, high / 16, top / 16, 2 / 16d);
                case SOUTH -> new AABB(low / 16, bottom / 16, 14 / 16d, high / 16, top / 16, 1);
                case WEST -> new AABB(0, bottom / 16, low / 16, 2 / 16d, top / 16, high / 16);
                case EAST -> new AABB(14 / 16d, bottom / 16, low / 16, 1, top / 16, high / 16);
                default -> throw new IllegalStateException();
            };
            openings.add(new Opening(face, box));
        }
        var result = new Profile(shape, List.copyOf(openings));
        PROFILES.put(be, result);
        return result;
    }

    @Override protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        return builder.with(WINDOWS, level.getBlockEntity(pos) instanceof LiddedVesselBlockEntity be ? profile(be) : EMPTY);
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, random, data, type);
        if (state != null && state.getValue(LiddedVesselBlock.DRIVE)) {
            var capped = new ArrayList<BakedQuad>(quads.size());
            for (var quad : quads) {
                if (quad.getDirection() == Direction.UP && Float.intBitsToFloat(quad.getVertices()[1]) == 1 && quad.getSprite().contents().name().equals(RIM)) continue;
                capped.add(quad.getDirection() == Direction.DOWN && quad.getSprite().contents().name().equals(BOTTOM) ? retexture(quad, PlantTankModels.VESSEL_CAP.getOriginal()) : quad);
            }
            quads = capped;
        }
        Profile profile = data.get(WINDOWS);
        if (profile == null || profile.openings().isEmpty()) return quads;
        for (var opening : profile.openings()) {
            List<BakedQuad> clipped = new ArrayList<>();
            for (var quad : quads) subtract(quad, opening.box(), clipped);
            quads = clipped;
        }
        return quads;
    }

    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite sprite) {
        var previous = quad.getSprite();int[] vertices = quad.getVertices().clone();int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            float u = (Float.intBitsToFloat(vertices[i * stride + 4]) - previous.getU0()) / (previous.getU1() - previous.getU0());
            float v = (Float.intBitsToFloat(vertices[i * stride + 5]) - previous.getV0()) / (previous.getV1() - previous.getV0());
            vertices[i * stride + 4] = Float.floatToRawIntBits(sprite.getU(u));
            vertices[i * stride + 5] = Float.floatToRawIntBits(sprite.getV(v));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), sprite, quad.isShade());
    }

    private static void subtract(BakedQuad quad, AABB hole, List<BakedQuad> result) {
        int axis = quad.getDirection().getAxis().ordinal(), u = axis == 0 ? 2 : 0, v = axis == 1 ? 2 : 1;
        int[] data = quad.getVertices();int stride = data.length / 4;
        float plane = Float.intBitsToFloat(data[axis]);
        if (plane < min(hole, axis) - EPSILON || plane > max(hole, axis) + EPSILON) { result.add(quad);return; }
        float u0 = Float.POSITIVE_INFINITY, v0 = u0, u1 = Float.NEGATIVE_INFINITY, v1 = u1;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(data[i * stride + u]), y = Float.intBitsToFloat(data[i * stride + v]);
            u0 = Math.min(u0, x);u1 = Math.max(u1, x);v0 = Math.min(v0, y);v1 = Math.max(v1, y);
        }
        float a = Math.max(u0, min(hole, u)), b = Math.min(u1, max(hole, u)), c = Math.max(v0, min(hole, v)), d = Math.min(v1, max(hole, v));
        if (b - a <= EPSILON || d - c <= EPSILON) { result.add(quad);return; }
        crop(quad, result, u, v, u0, v0, u1, v1, u0, v0, a, v1);
        crop(quad, result, u, v, u0, v0, u1, v1, b, v0, u1, v1);
        crop(quad, result, u, v, u0, v0, u1, v1, a, v0, b, c);
        crop(quad, result, u, v, u0, v0, u1, v1, a, d, b, v1);
    }

    private static void crop(BakedQuad quad, List<BakedQuad> result, int u, int v, float u0, float v0, float u1, float v1, float a, float b, float c, float d) {
        if (c - a <= EPSILON || d - b <= EPSILON) return;
        int[] source = quad.getVertices(), vertices = source.clone();int stride = source.length / 4;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(source[i * stride + u]) < (u0 + u1) / 2 ? a : c;
            float y = Float.intBitsToFloat(source[i * stride + v]) < (v0 + v1) / 2 ? b : d;
            vertices[i * stride + u] = Float.floatToRawIntBits(x);vertices[i * stride + v] = Float.floatToRawIntBits(y);
            for (int tex = 4; tex <= 5; tex++) {
                float value = 0;
                for (int j = 0; j < 4; j++) {
                    float weightU = Float.intBitsToFloat(source[j * stride + u]) < (u0 + u1) / 2 ? (u1 - x) / (u1 - u0) : (x - u0) / (u1 - u0);
                    float weightV = Float.intBitsToFloat(source[j * stride + v]) < (v0 + v1) / 2 ? (v1 - y) / (v1 - v0) : (y - v0) / (v1 - v0);
                    value += Float.intBitsToFloat(source[j * stride + tex]) * weightU * weightV;
                }
                vertices[i * stride + tex] = Float.floatToRawIntBits(value);
            }
        }
        result.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
    }

    private static float min(AABB box, int axis) { return (float) (axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ); }
    private static float max(AABB box, int axis) { return (float) (axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ); }
}
