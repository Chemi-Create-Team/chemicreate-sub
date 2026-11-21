package net.createplant.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MolecularModelRenderer {
    private static final int SPHERE_RINGS = 16, SPHERE_SIDES = 24, BOND_SIDES = 14;
    private static final float TAU = (float) (2 * Math.PI);
    private static final RenderType MESH = RenderType.create("createplant_molecular_model", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.TRIANGLES, 32768, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static final Vector3f LIGHT = new Vector3f(-.52f, -.65f, .82f).normalize();
    private static final Vector3f HIGHLIGHT = new Vector3f(LIGHT).add(0, 0, 1).normalize();
    private static final SphereVertex[][] SPHERE = sphereVertices();
    private static final float[][] CIRCLE = bondCircle();

    private record SphereVertex(float x, float y, float z, Lighting lighting) { }
    private record Lighting(float diffuse, float specular) {
        int shade(int color) {
            return 0xff000000 | scale(color >> 16) << 16 | scale(color >> 8) << 8 | scale(color);
        }
        private int scale(int channel) { return Math.min(255, Math.round((channel & 255) * diffuse + 255 * specular)); }
    }

    private MolecularModelRenderer() { }

    static void draw(GuiGraphics graphics, float x, float y, MoleculeStructures.Drawing drawing) {
        float rotation = (Util.getMillis() % 8000) * TAU / 8000;
        var view = new Quaternionf().rotationX(-.32f).rotateY(rotation).rotateZ(.14f);
        var atoms = drawing.atoms();
        float scale = 25 / Math.max(.51f, drawing.radius());
        var positions = new Vector3f[atoms.size()];
        for (int i = 0; i < atoms.size(); i++) {
            var atom = atoms.get(i);
            positions[i] = view.transform(new Vector3f(atom.x(), atom.y(), atom.depth())).mul(scale);
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 40);
        var matrix = pose.last().pose();
        var vertices = graphics.bufferSource().getBuffer(MESH);
        for (var bond : drawing.bonds()) {
            int start = bond.start(), end = bond.end();
            bond(vertices, matrix, positions[start], positions[end], color(atoms.get(start).element()), color(atoms.get(end).element()), bond.order());
        }
        for (int i = 0; i < atoms.size(); i++) {
            String element = atoms.get(i).element();
            float radius = switch (element) {
                case "H", "D", "T" -> 3.5f;
                case "F", "O" -> 4.85f;
                case "Cl", "S", "P", "Ar", "U" -> 6.1f;
                default -> 5.35f;
            };
            sphere(vertices, matrix, positions[i], radius, color(element));
        }
        graphics.bufferSource().endBatch(MESH);
        pose.popPose();
    }

    private static void sphere(VertexConsumer vertices, Matrix4f matrix, Vector3f center, float radius, int color) {
        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            for (int side = 0; side < SPHERE_SIDES; side++) {
                var a = SPHERE[ring][side];
                var b = SPHERE[ring][side + 1];
                var c = SPHERE[ring + 1][side + 1];
                var d = SPHERE[ring + 1][side];
                sphereVertex(vertices, matrix, center, radius, a, color);
                sphereVertex(vertices, matrix, center, radius, b, color);
                sphereVertex(vertices, matrix, center, radius, c, color);
                sphereVertex(vertices, matrix, center, radius, a, color);
                sphereVertex(vertices, matrix, center, radius, c, color);
                sphereVertex(vertices, matrix, center, radius, d, color);
            }
        }
    }

    private static void sphereVertex(VertexConsumer vertices, Matrix4f matrix, Vector3f center, float radius, SphereVertex norm, int color) {
        vertices.addVertex(matrix, center.x + radius * norm.x(), center.y + radius * norm.y(), center.z + radius * norm.z())
            .setColor(norm.lighting().shade(color));
    }

    private static void bond(VertexConsumer vertices, Matrix4f matrix, Vector3f start, Vector3f end, int firstColor, int secondColor, int order) {
        var axis = new Vector3f(end).sub(start).normalize();
        var u = (Math.abs(axis.z) < .9f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0)).cross(axis).normalize();
        var v = new Vector3f(u).cross(axis);
        var center = new Vector3f(start).lerp(end, .5f);
        float radius = order == 1 ? 1.5f : .85f;
        var normal = new Vector3f();
        var normals = new Vector3f[BOND_SIDES];
        var shades = new Lighting[BOND_SIDES];
        for (int side = 0; side < BOND_SIDES; side++) {
            normal.set(u).mul(CIRCLE[side][0]).fma(CIRCLE[side][1], v);
            normals[side] = new Vector3f(normal).mul(radius);
            shades[side] = light(normal.x, normal.y, normal.z);
        }
        var offset = new Vector3f();
        for (int i = 0; i < order; i++) {
            offset.set(u).mul((i - (order - 1) / 2f) * 2.2f);
            tube(vertices, matrix, start, center, offset, normals, shades, firstColor);
            tube(vertices, matrix, center, end, offset, normals, shades, secondColor);
        }
    }

    private static void tube(VertexConsumer vertices, Matrix4f matrix, Vector3f from, Vector3f to, Vector3f offset, Vector3f[] ring, Lighting[] shades, int color) {
        for (int i = 0; i < ring.length; i++) {
            int next = (i + 1) % ring.length;
            int first = shades[i].shade(color), second = shades[next].shade(color);
            tubeVertex(vertices, matrix, from, offset, ring[i], first);
            tubeVertex(vertices, matrix, from, offset, ring[next], second);
            tubeVertex(vertices, matrix, to, offset, ring[next], second);
            tubeVertex(vertices, matrix, from, offset, ring[i], first);
            tubeVertex(vertices, matrix, to, offset, ring[next], second);
            tubeVertex(vertices, matrix, to, offset, ring[i], first);
        }
    }

    private static void tubeVertex(VertexConsumer vertices, Matrix4f matrix, Vector3f center, Vector3f offset, Vector3f radial, int color) {
        vertices.addVertex(matrix, center.x + offset.x + radial.x, center.y + offset.y + radial.y, center.z + offset.z + radial.z).setColor(color);
    }

    private static SphereVertex[][] sphereVertices() {
        var rings = new SphereVertex[SPHERE_RINGS + 1][SPHERE_SIDES + 1];
        for (int ring = 0; ring <= SPHERE_RINGS; ring++) {
            double latitude = Math.PI * ring / SPHERE_RINGS;
            for (int side = 0; side <= SPHERE_SIDES; side++) {
                double longitude = TAU * side / SPHERE_SIDES;
                float x = (float) (Math.sin(latitude) * Math.cos(longitude));
                float y = (float) Math.cos(latitude);
                float z = (float) (Math.sin(latitude) * Math.sin(longitude));
                rings[ring][side] = new SphereVertex(x, y, z, light(x, y, z));
            }
        }
        return rings;
    }

    private static float[][] bondCircle() {
        var circle = new float[BOND_SIDES][2];
        for (int i = 0; i < BOND_SIDES; i++) {
            circle[i][0] = (float) Math.cos(TAU * i / BOND_SIDES);
            circle[i][1] = (float) Math.sin(TAU * i / BOND_SIDES);
        }
        return circle;
    }

    private static Lighting light(float x, float y, float z) {
        float diffuse = .37f + .68f * Math.max(0, x * LIGHT.x + y * LIGHT.y + z * LIGHT.z);
        float specular = .34f * (float) Math.pow(Math.max(0, x * HIGHLIGHT.x + y * HIGHLIGHT.y + z * HIGHLIGHT.z), 25);
        return new Lighting(diffuse, specular);
    }

    private static int color(String element) {
        return switch (element) {
            case "H" -> 0xf5f5f2;
            case "D", "T" -> 0xa5dee7;
            case "C" -> 0x737478;
            case "N" -> 0x4169db;
            case "O" -> 0xeb221b;
            case "F", "Cl" -> 0x55ba63;
            case "S" -> 0xe7c430;
            case "P" -> 0xfb943e;
            case "He", "Ar", "U" -> 0xb482d4;
            default -> 0xaba4d4;
        };
    }
}
