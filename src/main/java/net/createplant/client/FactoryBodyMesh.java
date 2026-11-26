package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.createplant.CreatePlant;
import net.createplant.factory.FactoryGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** One cached mesh per rigid body form. Only pose and light values change per instance. */
final class FactoryBodyMesh implements QuadMesh {
    private record Key(FactoryGeometry kind, int tiles) { }
    private record Vertex(float x, float y, float z, float u, float v, Direction normal, FactoryGeometry.Surface surface) { }
    private static final Map<Key, FactoryBodyMesh> MESHES = new ConcurrentHashMap<>();
    private final List<Vertex> vertices = new ArrayList<>();
    private final Vector4f sphere;
    private final Model model = new SingleMeshModel(this, Materials.SOLID_BLOCK);

    static FactoryBodyMesh get(FactoryGeometry kind, float length) {
        int tiles = kind == FactoryGeometry.DRUM ? Math.clamp(Math.round(length), 1, 15) : 1;
        return MESHES.computeIfAbsent(new Key(kind, tiles), FactoryBodyMesh::new);
    }

    private record Rect(double u0, double v0, double u1, double v1) {
        void subtract(Rect mask, List<Rect> out) {
            double lowU = Math.max(u0, mask.u0), highU = Math.min(u1, mask.u1);
            double lowV = Math.max(v0, mask.v0), highV = Math.min(v1, mask.v1);
            if (highU <= lowU + EPS || highV <= lowV + EPS) { out.add(this); return; }
            if (u0 < lowU - EPS) out.add(new Rect(u0, v0, lowU, v1));
            if (highU < u1 - EPS) out.add(new Rect(highU, v0, u1, v1));
            if (v0 < lowV - EPS) out.add(new Rect(lowU, v0, highU, lowV));
            if (highV < v1 - EPS) out.add(new Rect(lowU, highV, highU, v1));
        }
    }

    private static final double EPS = 1e-7;

    private FactoryBodyMesh(Key key) {
        var bounds = key.kind().extent();
        sphere = new Vector4f(0, 0, 0, (float) bounds.getSize());
        var pieces = key.kind().pieces();
        for (int i = 0; i < pieces.size(); i++) for (Direction direction : Direction.values())
            exposedFace(pieces, i, key.tiles(), direction);
    }

    private void exposedFace(List<FactoryGeometry.Piece> pieces, int index, int tiles, Direction direction) {
        var piece = pieces.get(index);
        var box = piece.bounds();
        int n = direction.getAxis().ordinal(), u = n == 0 ? 1 : 0, v = n == 2 ? 1 : 2;
        boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = positive ? max(box, n) : min(box, n);
        var exposed = new ArrayList<Rect>();
        exposed.add(rect(box, u, v));
        for (int i = 0; i < pieces.size() && !exposed.isEmpty(); i++) {
            if (i == index) continue;
            var other = pieces.get(i).bounds();
            if (min(other, n) > plane + EPS || max(other, n) < plane - EPS) continue;
            boolean covers = positive ? max(other, n) > plane + EPS : min(other, n) < plane - EPS;
            if (!covers && i >= index) continue;
            var mask = rect(other, u, v);
            var face = rect(box, u, v);
            if (mask.u0 >= face.u1 - EPS || mask.u1 <= face.u0 + EPS || mask.v0 >= face.v1 - EPS || mask.v1 <= face.v0 + EPS) continue;
            var remainder = new ArrayList<Rect>();
            for (var area : exposed) area.subtract(mask, remainder);
            exposed = remainder;
        }
        for (var area : exposed) tileFace(area, plane, direction, n, u, v, n == 2 ? 1 : tiles, piece.surface());
    }

    private void tileFace(Rect area, double plane, Direction d, int n, int u, int v, int vScale, FactoryGeometry.Surface surface) {
        boolean normal = d == Direction.EAST || d == Direction.DOWN || d == Direction.SOUTH;
        for (double a = area.u0; a < area.u1 - EPS;) {
            double tileU = Math.floor(a + EPS), endU = Math.min(area.u1, tileU + 1);
            for (double b = area.v0; b < area.v1 - EPS;) {
                double tileV = Math.floor(b * vScale + EPS), endV = Math.min(area.v1, (tileV + 1) / vScale);
                vertex(plane, a, b, tileU, tileV, n, u, v, vScale, d, surface);
                vertex(plane, normal ? endU : a, normal ? b : endV, tileU, tileV, n, u, v, vScale, d, surface);
                vertex(plane, endU, endV, tileU, tileV, n, u, v, vScale, d, surface);
                vertex(plane, normal ? a : endU, normal ? endV : b, tileU, tileV, n, u, v, vScale, d, surface);
                b = endV;
            }
            a = endU;
        }
    }

    private void vertex(double plane, double a, double b, double tileU, double tileV, int n, int u, int v, int vScale,
                        Direction direction, FactoryGeometry.Surface surface) {
        float x = (float) (n == 0 ? plane : u == 0 ? a : b);
        float y = (float) (n == 1 ? plane : u == 1 ? a : b);
        float z = (float) (n == 2 ? plane : v == 2 ? b : a);
        vertices.add(new Vertex(x, y, z, (float) (a - tileU), (float) (b * vScale - tileV), direction, surface));
    }

    private static Rect rect(AABB box, int u, int v) { return new Rect(min(box, u), min(box, v), max(box, u), max(box, v)); }
    private static double min(AABB box, int axis) { return axis == 0 ? box.minX : axis == 1 ? box.minY : box.minZ; }
    private static double max(AABB box, int axis) { return axis == 0 ? box.maxX : axis == 1 ? box.maxY : box.maxZ; }

    Model model() { return model; }
    @Override public int vertexCount() { return vertices.size(); }
    @Override public Vector4fc boundingSphere() { return sphere; }

    private static TextureAtlasSprite[] sprites() {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var surfaces = FactoryGeometry.Surface.values();
        var sprites = new TextureAtlasSprite[surfaces.length];
        for (var surface : surfaces) sprites[surface.ordinal()] = atlas.apply(CreatePlant.location("block/continuous_factory/" + surface.name().toLowerCase(Locale.ROOT)));
        return sprites;
    }

    @Override public void write(MutableVertexList output) {
        var sprites = sprites();
        for (int i = 0; i < vertices.size(); i++) {
            var v = vertices.get(i);
            var sprite = sprites[v.surface().ordinal()];
            output.x(i, v.x()); output.y(i, v.y()); output.z(i, v.z());
            output.u(i, sprite.getU(v.u())); output.v(i, sprite.getV(v.v()));
            output.r(i, 1); output.g(i, 1); output.b(i, 1); output.a(i, 1);
            output.normalX(i, v.normal().getStepX()); output.normalY(i, v.normal().getStepY()); output.normalZ(i, v.normal().getStepZ());
            output.overlay(i, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            output.light(i, v.surface() == FactoryGeometry.Surface.HOT ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : 0);
        }
    }

    void render(PoseStack stack, VertexConsumer buffer, int light) {
        var sprites = sprites();
        var pose = stack.last();
        for (var v : vertices) {
            var sprite = sprites[v.surface().ordinal()];
            float shade = switch (v.normal()) { case UP -> 1; case DOWN -> .57f; case NORTH, SOUTH -> .81f; case EAST, WEST -> .73f; };
            int lit = v.surface() == FactoryGeometry.Surface.HOT ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
            buffer.addVertex(pose, v.x(), v.y(), v.z()).setColor(shade, shade, shade, 1)
                .setUv(sprite.getU(v.u()), sprite.getV(v.v())).setLight(lit)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setNormal(pose, v.normal().getStepX(), v.normal().getStepY(), v.normal().getStepZ());
        }
    }
}
