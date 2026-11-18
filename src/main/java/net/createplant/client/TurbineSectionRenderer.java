package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createplant.block.TurbineUnit;
import net.createplant.block.entity.SteamTurbineBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.List;

/** Cached octagonal shells, flanges and automatically closed terminal faces. */
final class TurbineSectionRenderer {
    private record Mesh(
            List<RenderMesh.Quad> shell,
            List<RenderMesh.Quad> ends,
            List<RenderMesh.Quad> fittings) {}

    private static final Mesh[] MESHES = {mesh(1), mesh(4)};

    private TurbineSectionRenderer() {}

    static void renderItem(TurbineUnit.Stage stage, boolean casingOnly, boolean endOnly,
            PoseStack pose, MultiBufferSource buffers, int light) {
        var mesh = MESHES[stage == TurbineUnit.Stage.LOW ? 1 : 0];
        float scale = .9f / (stage.radius() * 2 + 1);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.scale(scale, scale, scale);
        if (!endOnly) {
            draw(mesh.shell(), "block/turbine/high_pressure_casing", pose, buffers, light);
            draw(mesh.fittings(), "block/plant/copper_flange", pose, buffers, light);
        }
        if (!casingOnly) {
            draw(mesh.ends(), "block/electric_utilities/steel", pose, buffers, light);
            pose.pushPose();
            pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.PI));
            draw(mesh.ends(), "block/electric_utilities/steel", pose, buffers, light);
            pose.popPose();
        }
        if (!casingOnly && !endOnly) {
            pose.translate(-.5, -.5, -.5);
            TurbineShaftVisual.render(Direction.Axis.X, 0, pose, buffers.getBuffer(RenderType.solid()), light);
        }
        pose.popPose();
    }

    static void render(
            SteamTurbineBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        var unit = be.unitLayout();
        var mesh = MESHES[unit.lowPressure() ? 1 : 0];
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(RenderMesh.axisTransform(unit.axis()));
        draw(mesh.shell(), "block/turbine/high_pressure_casing", pose, buffers, light);
        draw(mesh.fittings(), "block/plant/copper_flange", pose, buffers, light);
        for (var direction : Direction.AxisDirection.values()) {
            var side = Direction.fromAxisAndDirection(unit.axis(), direction);
            if (TurbineUnit.connected(be.getLevel(), unit, side)) continue;
            pose.pushPose();
            if (direction == Direction.AxisDirection.POSITIVE)
                pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.PI));
            draw(mesh.ends(), "block/electric_utilities/steel", pose, buffers, light);
            pose.popPose();
        }
        pose.translate(-.5, -.5, -.5);
        float time = be.getLevel() == null ? 0 : AnimationTickHolder.getRenderTime(be.getLevel());
        float angle = (time * be.getSpeed() * .3f % 360) * (float) Math.PI / 180;
        TurbineShaftVisual.render(
                Direction.Axis.X, angle, pose, buffers.getBuffer(RenderType.solid()), light);
        pose.pushPose();
        pose.translate(.5, .5, .5);
        float bladeScale =
                unit.stage() == TurbineUnit.Stage.HIGH ? 1 : (unit.radius() + .20f) / 1.95f;
        pose.scale(1, bladeScale, bladeScale);
        pose.translate(-.5, -.5, -.5);
        SteamTurbineRenderer.renderImpeller(
                be,
                Direction.Axis.X,
                be.getBlockState(),
                pose,
                buffers.getBuffer(RenderType.solid()),
                light);
        pose.popPose();
        pose.popPose();
    }

    private static void draw(
            List<RenderMesh.Quad> quads,
            String texture,
            PoseStack pose,
            MultiBufferSource buffers,
            int light) {
        var sprite = RenderMesh.sprite(texture);
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (var quad : quads) RenderMesh.drawQuad(pose.last(), buffer, sprite, quad, light, 0);
    }

    private static Mesh mesh(int radius) {
        var shell = new ArrayList<RenderMesh.Quad>();
        var ends = new ArrayList<RenderMesh.Quad>();
        var fittings = new ArrayList<RenderMesh.Quad>();
        float outer = radius + .46f, inner = outer - .16f;
        tube(shell, -.5f, .5f, inner, outer - .09f);
        tube(shell, -.5f, -.36f, inner, outer);
        tube(shell, .36f, .5f, inner, outer);
        // A split flange follows the horizontal seam, as on the reference casing.
        box(shell, -.5f, -.07f, -outer, .5f, .07f, -inner);
        box(shell, -.5f, -.07f, inner, .5f, .07f, outer);
        // Feet occupy the already-reserved bottom side voxels.
        box(shell, -.45f, -outer, -outer * .42f, .45f, -inner + .10f, outer * .42f);
        float[][] rim = octagon(inner), hub = octagon(.125f);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            quad(
                    ends,
                    new float[] {
                        -.495f, rim[i][0], rim[i][1], -.495f, rim[j][0], rim[j][1], -.495f,
                        hub[j][0], hub[j][1], -.495f, hub[i][0], hub[i][1]
                    },
                    -1,
                    0,
                    0);
            float y = (rim[i][0] + rim[j][0]) * .48f, z = (rim[i][1] + rim[j][1]) * .48f;
            box(fittings, -.505f, y - .05f, z - .05f, -.35f, y + .05f, z + .05f);
        }
        // Radial pipe flange: the adjacent block is a real fluid port.
        box(fittings, -.28f, -.24f, inner - .02f, .28f, .24f, outer);
        tube(ends, -.5f, -.40f, .125f, .37f);
        for (var q : ends) for (int i = 1; i < q.uv.length; i += 2) q.uv[i] *= 2;
        for (var q : fittings) for (int i = 1; i < q.uv.length; i += 2) q.uv[i] *= 2;
        return new Mesh(List.copyOf(shell), List.copyOf(ends), List.copyOf(fittings));
    }

    private static float[][] octagon(float r) {
        float t = r * ((float) Math.sqrt(2) - 1);
        return new float[][] {
            {-r, -t}, {-t, -r}, {t, -r}, {r, -t}, {r, t}, {t, r}, {-t, r}, {-r, t}
        };
    }

    private static void tube(
            List<RenderMesh.Quad> out, float x0, float x1, float inner, float outer) {
        float[][] a = octagon(outer), b = octagon(inner);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            float y = (a[i][0] + a[j][0]) * .5f, z = (a[i][1] + a[j][1]) * .5f;
            float length = (float) Math.hypot(y, z);
            y /= length;
            z /= length;
            quad(
                    out,
                    new float[] {
                        x0, a[i][0], a[i][1], x1, a[i][0], a[i][1], x1, a[j][0], a[j][1], x0,
                        a[j][0], a[j][1]
                    },
                    0,
                    y,
                    z);
            quad(
                    out,
                    new float[] {
                        x0, b[j][0], b[j][1], x1, b[j][0], b[j][1], x1, b[i][0], b[i][1], x0,
                        b[i][0], b[i][1]
                    },
                    0,
                    -y,
                    -z);
            for (float x : new float[] {x0, x1})
                quad(
                        out,
                        new float[] {
                            x, a[i][0], a[i][1], x, a[j][0], a[j][1], x, b[j][0], b[j][1], x,
                            b[i][0], b[i][1]
                        },
                        x == x0 ? -1 : 1,
                        0,
                        0);
        }
    }

    private static void box(
            List<RenderMesh.Quad> out, float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(out, new float[] {x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1}, -1, 0, 0);
        quad(out, new float[] {x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0}, 1, 0, 0);
        quad(out, new float[] {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0}, 0, -1, 0);
        quad(out, new float[] {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, 0, 1, 0);
        quad(out, new float[] {x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0}, 0, 0, -1);
        quad(out, new float[] {x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1}, 0, 0, 1);
    }

    private static float length(float[] v, int a, int b) {
        float x = v[a * 3] - v[b * 3],
                y = v[a * 3 + 1] - v[b * 3 + 1],
                z = v[a * 3 + 2] - v[b * 3 + 2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static void quad(
            List<RenderMesh.Quad> out, float[] vertices, float nx, float ny, float nz) {
        float width = Math.max(length(vertices, 0, 1), length(vertices, 3, 2));
        float height = Math.max(length(vertices, 1, 2), length(vertices, 0, 3));
        int cols = Math.max(1, (int) Math.ceil(width)), rows = Math.max(1, (int) Math.ceil(height));
        for (int col = 0; col < cols; col++)
            for (int row = 0; row < rows; row++) {
                float u0 = col / (float) cols,
                        u1 = (col + 1) / (float) cols,
                        v0 = row / (float) rows,
                        v1 = (row + 1) / (float) rows;
                var q = new RenderMesh.Quad();
                q.vertices = new float[12];
                q.normal = new float[] {nx, ny, nz};
                float[] us = {u0, u1, u1, u0}, vs = {v0, v0, v1, v1};
                for (int i = 0; i < 4; i++)
                    for (int axis = 0; axis < 3; axis++) {
                        float bottom = vertices[axis] * (1 - us[i]) + vertices[3 + axis] * us[i];
                        float top = vertices[9 + axis] * (1 - us[i]) + vertices[6 + axis] * us[i];
                        q.vertices[i * 3 + axis] = bottom * (1 - vs[i]) + top * vs[i];
                    }
                float u = width / cols, v = .5f * height / rows;
                q.uv = new float[] {0, 0, u, 0, u, v, 0, v};
                out.add(q);
            }
    }
}
