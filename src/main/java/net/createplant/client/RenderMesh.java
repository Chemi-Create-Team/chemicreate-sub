package net.createplant.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createplant.CreatePlant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;

/** Exported JSON quad meshes drawn by the turbine and compressor renderers. */
final class RenderMesh {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, Data> LOADED = new HashMap<>();
    private static final Set<ResourceLocation> WARNED_ABOUT_MISSING_MESH = new HashSet<>();

    private RenderMesh() {
    }

    static Data load(ResourceLocation location) {
        Data cached = LOADED.get(location);
        if (cached != null) {
            return cached;
        }
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
            Data data = GSON.fromJson(reader, Data.class);
            LOADED.put(location, data);
            return data;
        } catch (IOException | RuntimeException e) {
            if (WARNED_ABOUT_MISSING_MESH.add(location)) {
                System.err.println("Failed to load render mesh: " + location + " (" + e + ")");
            }
            return null;
        }
    }

    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(CreatePlant.location(path));
    }

    static void drawQuad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Quad quad,
        int light, float surfaceOffset) {
        drawQuad(pose, buffer, sprite, quad, light, surfaceOffset, 255, 255, 255, 255);
    }

    static void drawQuad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Quad quad,
        int light, float surfaceOffset, int red, int green, int blue, int alpha) {
        for (int i = 0; i < 4; i++) {
            vertex(pose, buffer, sprite, quad, light, i, surfaceOffset, quad.vertices[i * 3], quad.vertices[i * 3 + 1],
                quad.vertices[i * 3 + 2], red, green, blue, alpha);
        }
    }

    static void drawQuadReversed(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Quad quad,
        int light, float surfaceOffset) {
        drawQuadReversed(pose, buffer, sprite, quad, light, surfaceOffset, 255, 255, 255, 255);
    }

    static void drawQuadReversed(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Quad quad,
        int light, float surfaceOffset, int red, int green, int blue, int alpha) {
        for (int i = 3; i >= 0; i--) {
            vertex(pose, buffer, sprite, quad, light, i, -surfaceOffset, quad.vertices[i * 3],
                quad.vertices[i * 3 + 1], quad.vertices[i * 3 + 2], red, green, blue, alpha);
        }
    }

    static void vertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Quad quad, int light,
        int index, float surfaceOffset, float x, float y, float z, int red, int green, int blue, int alpha) {
        int uv = index * 2;
        float normalSign = surfaceOffset >= 0 ? 1 : -1;
        buffer.addVertex(pose, x + quad.normal[0] * surfaceOffset, y + quad.normal[1] * surfaceOffset,
                z + quad.normal[2] * surfaceOffset)
            .setColor(red, green, blue, alpha)
            .setUv(Mth.lerp(quad.uv[uv], sprite.getU0(), sprite.getU1()),
                Mth.lerp(quad.uv[uv + 1], sprite.getV0(), sprite.getV1()))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, quad.normal[0] * normalSign, quad.normal[1] * normalSign, quad.normal[2] * normalSign);
    }

    static Quaternionf axisTransform(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Quaternionf();
            case Y -> new Quaternionf().rotateZ((float) Math.toRadians(90));
            case Z -> new Quaternionf().rotateY((float) Math.toRadians(-90));
        };
    }

    static class Data {
        List<Quad> quads;
    }

    static class Quad {
        boolean animated;
        int texture;
        float[] vertices;
        float[] uv;
        float[] normal;

        Quad offset(float x, float y, float z) {
            Quad copy = new Quad();
            copy.texture = texture;
            copy.uv = uv;
            copy.normal = normal;
            copy.vertices = vertices.clone();
            for (int i = 0; i < 4; i++) {
                copy.vertices[i * 3] += x;
                copy.vertices[i * 3 + 1] += y;
                copy.vertices[i * 3 + 2] += z;
            }
            return copy;
        }
    }
}
