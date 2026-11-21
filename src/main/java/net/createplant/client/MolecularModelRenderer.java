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
    private static final float TAU = (float) (2 * Math.PI);
    private static final float MODEL_RADIUS = 28.25f;
    private static final RenderType MESH = RenderType.create("createplant_molecular_model", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, 8192, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER).setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static final Vector3f LIGHT = new Vector3f(-.48f, -.73f, .65f).normalize();

    private MolecularModelRenderer() { }

    static void draw(GuiGraphics graphics, float x, float y, MoleculeStructures.Drawing drawing) {
        float rotation = (Util.getMillis() % 8000) * TAU / 8000;
        var view = new Quaternionf().rotationX(-.32f).rotateY(rotation).rotateZ(.14f);
        var localX = view.transform(new Vector3f(1, 0, 0));
        var localY = view.transform(new Vector3f(0, 1, 0));
        var localZ = view.transform(new Vector3f(0, 0, 1));
        var atoms = drawing.atoms();
        float scale = MODEL_RADIUS / Math.max(.51f, drawing.radius());
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
            bond(vertices, matrix, positions[start], positions[end], localY, localZ,
                color(atoms.get(start).element()), color(atoms.get(end).element()), bond.order());
        }
        for (int i = 0; i < atoms.size(); i++) {
            String element = atoms.get(i).element();
            if (element.equals("*")) continue;
            float half = switch (element) {
                case "H", "D", "T" -> 2.35f;
                case "F", "O" -> 3.3f;
                case "Na", "Cl", "S", "P", "Ar", "U", "Pu" -> 4.1f;
                default -> 3.75f;
            };
            box(vertices, matrix, positions[i], localX, half, localY, half, localZ, half, color(element));
        }
        graphics.bufferSource().endBatch(MESH);
        pose.popPose();
    }

    private static void bond(VertexConsumer vertices, Matrix4f matrix, Vector3f start, Vector3f end, Vector3f up, Vector3f front,
        int firstColor, int secondColor, int order) {
        var axis = new Vector3f(end).sub(start);
        float length = axis.length();
        if (length < .001f) return;
        axis.div(length);
        var u = new Vector3f(Math.abs(axis.dot(front)) < .9f ? front : up).cross(axis).normalize();
        var v = new Vector3f(axis).cross(u);
        float quarter = length * .25f, thickness = order == 1 ? 1.05f : .62f;
        for (int i = 0; i < order; i++) {
            float offset = (i - (order - 1) / 2f) * 1.85f;
            var first = new Vector3f(start).fma(quarter, axis).fma(offset, u);
            var second = new Vector3f(first).fma(quarter * 2, axis);
            box(vertices, matrix, first, axis, quarter, u, thickness, v, thickness, firstColor);
            box(vertices, matrix, second, axis, quarter, u, thickness, v, thickness, secondColor);
        }
    }

    private static void box(VertexConsumer vertices, Matrix4f matrix, Vector3f center,
        Vector3f x, float sx, Vector3f y, float sy, Vector3f z, float sz, int color) {
        faces(vertices, matrix, center, x, sx, y, sy, z, sz, color);
        faces(vertices, matrix, center, y, sy, z, sz, x, sx, color);
        faces(vertices, matrix, center, z, sz, x, sx, y, sy, color);
    }

    private static void faces(VertexConsumer vertices, Matrix4f matrix, Vector3f center, Vector3f normal, float distance,
        Vector3f u, float sizeU, Vector3f v, float sizeV, int color) {
        for (int sign = -1; sign <= 1; sign += 2) {
            var face = new Vector3f(center).fma(sign * distance, normal);
            float brightness = .63f + .37f * Math.max(0, sign * normal.dot(LIGHT));
            int shaded = 0xff000000 | channel(color >> 16, brightness) << 16
                | channel(color >> 8, brightness) << 8 | channel(color, brightness);
            vertex(vertices, matrix, face, u, -sizeU, v, -sizeV, shaded);
            vertex(vertices, matrix, face, u, sizeU, v, -sizeV, shaded);
            vertex(vertices, matrix, face, u, sizeU, v, sizeV, shaded);
            vertex(vertices, matrix, face, u, -sizeU, v, sizeV, shaded);
        }
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vector3f face, Vector3f u, float a, Vector3f v, float b, int color) {
        vertices.addVertex(matrix, face.x + u.x * a + v.x * b, face.y + u.y * a + v.y * b, face.z + u.z * a + v.z * b).setColor(color);
    }

    private static int channel(int value, float brightness) { return Math.round((value & 255) * brightness); }

    private static int color(String element) {
        return switch (element) {
            case "H" -> 0xe8ebec;
            case "D", "T" -> 0xa5dee7;
            case "C" -> 0x92969e;
            case "N" -> 0x6285d8;
            case "O" -> 0xe35c51;
            case "F", "Cl" -> 0x73bc68;
            case "S" -> 0xdac24e;
            case "P" -> 0xdd9858;
            case "Na" -> 0xa6d0ed;
            case "Al" -> 0x9bc0bc;
            case "He", "Ar", "U", "Pu" -> 0xb496d5;
            default -> 0xaba4c0;
        };
    }
}
