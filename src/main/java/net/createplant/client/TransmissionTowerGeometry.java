package net.createplant.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import net.createplant.CreatePlant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;

@EventBusSubscriber(modid = CreatePlant.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TransmissionTowerGeometry extends SimpleUnbakedGeometry<TransmissionTowerGeometry> {
    private static final Vec3 CENTER = new Vec3(.5, .5, .5);
    private static final int[][] FACES = {
        {0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}
    };
    private static final int[] U = {0, 0, 16, 16};
    private static final int[] V = {16, 0, 0, 16};

    private record Beam(Vec3 from, Vec3 to, double width, double depth, String texture) { }
    private final List<Beam> beams;

    private TransmissionTowerGeometry(List<Beam> beams) {
        this.beams = List.copyOf(beams);
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<TransmissionTowerGeometry> loader = (json, context) -> {
            var beams = new ArrayList<Beam>();
            for (var entry : json.getAsJsonArray("beams")) {
                var value = entry.getAsJsonObject();
                Vec3 from = point(value.getAsJsonArray("from"));
                Vec3 to = point(value.getAsJsonArray("to"));
                double width = value.get("width").getAsDouble();
                double depth = value.has("depth") ? value.get("depth").getAsDouble() : width;
                if (!(from.distanceToSqr(to) > 1e-10) || !(width > 0) || !(depth > 0)) throw new JsonParseException("Degenerate beam");
                beams.add(new Beam(from, to, width, depth, value.get("texture").getAsString()));
            }
            return new TransmissionTowerGeometry(beams);
        };
        event.register(CreatePlant.location("lattice_beams"), loader);
    }

    private static Vec3 point(JsonArray coordinates) {
        return new Vec3(coordinates.get(0).getAsDouble(), coordinates.get(1).getAsDouble(), coordinates.get(2).getAsDouble());
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        var matrix = state.getRotation().getMatrix();
        var textures = new HashMap<String, TextureAtlasSprite>();
        for (Beam beam : beams) {
            var texture = textures.computeIfAbsent(beam.texture(), key -> sprites.apply(context.getMaterial(key)));
            Vec3 axis = beam.to().subtract(beam.from()).normalize();
            Vec3 reference = Math.abs(axis.y) > .98 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 u = axis.cross(reference).normalize().scale(beam.width() / 2);
            Vec3 v = axis.cross(u).normalize().scale(beam.depth() / 2);
            var points = new Vec3[8];
            for (int end = 0; end < 2; end++) {
                Vec3 origin = end == 0 ? beam.from() : beam.to();
                for (int corner = 0; corner < 4; corner++) {
                    Vec3 local = origin.add(corner < 2 ? u : u.reverse())
                        .add(corner == 0 || corner == 3 ? v : v.reverse()).subtract(CENTER);
                    var transformed = matrix.transformPosition(new Vector3f((float) local.x, (float) local.y, (float) local.z));
                    points[4 * end + corner] = new Vec3(transformed.x, transformed.y, transformed.z).add(CENTER);
                }
            }
            Vec3 center = points[0].add(points[6]).scale(.5);
            for (int[] face : FACES) {
                Vec3 start = points[face[0]];
                Vec3 normal = points[face[1]].subtract(start).cross(points[face[2]].subtract(start)).normalize();
                boolean reverse = normal.dot(start.subtract(center)) < 0;
                if (reverse) normal = normal.reverse();
                var quad = new QuadBakingVertexConsumer();
                quad.setSprite(texture);
                quad.setDirection(Direction.getNearest(normal.x, normal.y, normal.z));
                quad.setTintIndex(-1);
                quad.setShade(true);
                for (int corner = 0; corner < 4; corner++) {
                    Vec3 point = points[face[reverse ? 3 - corner : corner]];
                    quad.addVertex((float) point.x, (float) point.y, (float) point.z).setColor(255, 255, 255, 255)
                        .setUv(texture.getU(U[corner] / 16f), texture.getV(V[corner] / 16f))
                        .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
                }
                builder.addUnculledFace(quad.bakeQuad());
            }
        }
    }
}
