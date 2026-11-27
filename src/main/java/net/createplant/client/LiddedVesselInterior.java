package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createplant.block.LiddedVesselBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Shared cross-sections for the stainless lining, sight tunnels and fluid inside a three-wide tank. */
final class LiddedVesselInterior {
    static final float FLOOR = LiddedVesselBlock.DISH_FLOOR_HEIGHT, SHOULDER = LiddedVesselBlock.DISH_RIM_HEIGHT, CENTRE = 24;
    static final float ROOF_INSERT_HALF = LiddedVesselBlock.BORE_RADIUS + 1;
    static final Direction[] WINDOW_FACES = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    private static final int SIDES = 32, PER_QUADRANT = SIDES / 4;
    private static final float WINDOW_HALF = 4, SHELL_RADIUS = 22, FLUID_INSET = .06f;
    private static final Ring[] DISH = new Ring[(int) (SHOULDER - FLOOR) + 1];
    private static final float[] ARC = arc();

    static {
        for (int i = 0; i < DISH.length; i++) {
            int y = (int) FLOOR + i;
            float radius = LiddedVesselBlock.boreRadius(y);
            float slope = i == 0 ? 5 : (LiddedVesselBlock.boreRadius(y + 1) - LiddedVesselBlock.boreRadius(y - 1)) / 2;
            if (i == DISH.length - 1) slope = 0;
            var points = new Point[SIDES];
            double edge = Math.asin(WINDOW_HALF / radius);
            for (int segment = 0; segment < SIDES; segment++) {
                int cardinal = ((segment + 4) / PER_QUADRANT) % 4;
                int offset = (segment + 4) % PER_QUADRANT - 4;
                float angle = (float) (cardinal * Math.PI / 2 + windowAngle(edge, offset));
                float x = (float) Math.cos(angle), z = (float) Math.sin(angle);
                points[segment] = new Point(radius * x, radius * z, x, z);
            }
            DISH[i] = new Ring(FLOOR + i, points, slope);
        }
    }

    private LiddedVesselInterior() { }

    // Each notch ends exactly at +/-4 px across its own wall, including on the curved dish.
    private static double windowAngle(double edge, int offset) {
        int distance = Math.abs(offset);
        double angle = distance <= 2 ? edge * distance / 2 : edge + (Math.PI / 4 - edge) * (distance - 2) / 2;
        return Math.copySign(angle, offset);
    }

    private static Ring ring(float y) {
        if (y >= SHOULDER) return DISH[DISH.length - 1];
        int low = Math.max(0, (int) (y - FLOOR));
        float t = y - DISH[low].y();
        if (t <= 0) return DISH[low];
        Ring a = DISH[low], b = DISH[low + 1];
        var points = new Point[SIDES];
        for (int i = 0; i < SIDES; i++) points[i] = a.points()[i].lerp(b.points()[i], t);
        return new Ring(y, points, mix(a.slope(), b.slope(), t));
    }

    static float itemOrbit(float y) {
        float localY = y * 16;
        int low = (int) localY;
        float radius = mix(LiddedVesselBlock.boreRadius(low), LiddedVesselBlock.boreRadius(low + 1), localY - low);
        return Math.max(0, radius / 16 - .39f);
    }

    static void lining(PoseStack ms, VertexConsumer vc, int height, int windows, boolean stirred, TextureAtlasSprite steel, int light, int overlay) {
        float roof = height * 16 - 2;
        for (int layer = 0; layer < height; layer++) {
            int start = layer * 16, end = Math.min(start + 16, (int) roof);
            int sill = start + (layer == 0 ? (int) SHOULDER : 0), lintel = layer == height - 1 ? start + 12 : end;
            int openings = windows >>> (layer * 4) & 15;
            for (int bottom = Math.max(start, (int) FLOOR); bottom < end; ) {
                int top = bottom < SHOULDER ? bottom + 1 : bottom < lintel ? lintel : end;
                int active = bottom >= sill && top <= lintel ? openings : 0;
                Ring lower = ring(bottom), upper = ring(top);
                sleeve(vc, ms, lower, bottom, upper, top, steel, -1, active, 0, -1, light, overlay);
                if (active != 0) {
                    for (int side = 0; side < 4; side++) {
                        if ((active & 1 << side) == 0) continue;
                        int first = side * PER_QUADRANT - 2, last = first + 4;
                        Direction face = WINDOW_FACES[side];
                        jamb(vc, ms, lower, upper, bottom, top, first, face, 1, steel, light, overlay);
                        jamb(vc, ms, lower, upper, bottom, top, last, face, -1, steel, light, overlay);
                        if (bottom == sill && (layer == 0 || (windows & 1 << ((layer - 1) * 4 + side)) == 0))
                            ledge(vc, ms, lower, bottom, first, face, 1, steel, light, overlay);
                        if (top == lintel && (layer == height - 1 || (windows & 1 << ((layer + 1) * 4 + side)) == 0))
                            ledge(vc, ms, upper, top, first, face, -1, steel, light, overlay);
                    }
                }
                bottom = top;
            }
        }

        // Close the tiny gaps between the ordinary square hatch's corners and the round bore.
        if (!stirred) {
            Ring lip = ring(roof);
            for (int i = 0; i < SIDES; i++) {
                Point a = lip.at(i), b = lip.at(i + 1);
                horizontal(vc, ms.last(), steel, a, b, b.toSquare(SHELL_RADIUS), a.toSquare(SHELL_RADIUS), roof, 1, light, overlay);
            }
        }
    }

    /** The roof round the bore; its top continues the stainless tank's 3x3 cap sheet, so the roof reads as that top with a round hole cut in it. */
    static void roundRoof(PoseStack ms, VertexConsumer vc, int height, TextureAtlasSprite steel, TextureAtlasSprite cap, int light, int overlay) {
        Ring rim = DISH[DISH.length - 1];
        float top = height * 16;
        var pose = ms.last();
        for (int i = 0; i < SIDES; i++) {
            Point a = rim.at(i), b = rim.at(i + 1), A = a.toSquare(ROOF_INSERT_HALF), B = b.toSquare(ROOF_INSERT_HALF);
            for (Point p : new Point[] {a, b, B, A}) capVertex(vc, pose, cap, p, top, light, overlay);
            horizontal(vc, pose, steel, b, a, A, B, top - 2, -1, light, overlay);
            strip(vc, pose, steel, a, b, a, b, top - 2, top, 0, (ARC[i + 1] - ARC[i]) / 16, 0, .125f, 0, 0, -1, -1, 0, -1, light, overlay);
        }
    }

    // The 3x3 cap occupies the left, middle and right columns and the top three rows of Create's rectangle sheet, a contiguous 48 px square.
    private static void capVertex(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite cap, Point point, float y, int light, int overlay) {
        vertex(vc, pose, cap, point, y, (16 + CENTRE + point.x()) / 64, (CENTRE + point.z()) / 64, 0, 1, 0, 0, -1, light, overlay);
    }

    private static void horizontal(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, Point a, Point b, Point c, Point d,
            float y, int normal, int light, int overlay) {
        flat(vc, pose, sprite, a, y, normal, -1, light, overlay);
        flat(vc, pose, sprite, b, y, normal, -1, light, overlay);
        flat(vc, pose, sprite, c, y, normal, -1, light, overlay);
        flat(vc, pose, sprite, d, y, normal, -1, light, overlay);
    }

    static void fluid(FluidStack stack, float bottom, float top, VertexConsumer vc, PoseStack ms, int light, int overlay) {
        if (top <= bottom) return;
        bottom *= 16;
        top *= 16;
        var properties = IClientFluidTypeExtensions.of(stack.getFluid());
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        var still = atlas.apply(FluidStateVisuals.texture(stack, properties.getStillTexture(stack)));
        var sideTexture = FluidStateVisuals.texture(stack, properties.getFlowingTexture(stack));
        var flowing = sideTexture == null ? still : atlas.apply(sideTexture);
        int color = properties.getTintColor(stack);
        if (!FluidStateVisuals.hasRenderingExtension(stack)) color = FluidStateVisuals.tint(stack, color);
        int luminosity = FluidStateVisuals.luminosity(stack, stack.getFluidType().getLightLevel(stack));
        light = LightTexture.pack(Math.max(LightTexture.block(light), luminosity), LightTexture.sky(light));
        Ring lower = ring(bottom);
        cap(vc, ms, lower, bottom, still, -1, color, light, overlay);
        for (float y = bottom; y < top; ) {
            float next = Math.min(top, y < SHOULDER ? (float) Math.floor(y) + 1 : (float) Math.floor(y / 16 + 1) * 16);
            Ring upper = ring(next);
            sleeve(vc, ms, lower, y, upper, next, flowing, 1, 0, FLUID_INSET, color, light, overlay);
            y = next;
            lower = upper;
        }
        cap(vc, ms, lower, top, still, 1, color, light, overlay);
    }

    private static void sleeve(VertexConsumer vc, PoseStack ms, Ring lower, float bottom, Ring upper, float top, TextureAtlasSprite sprite,
            int normal, int windows, float inset, int color, int light, int overlay) {
        var pose = ms.last();
        float v0 = ((float) Math.floor(bottom / 16) * 16 + 16 - top) / 16, v1 = v0 + (top - bottom) / 16;
        float bottomNormal = normal / (float) Math.sqrt(1 + lower.slope() * lower.slope());
        float topNormal = normal / (float) Math.sqrt(1 + upper.slope() * upper.slope());
        for (int i = 0; i < SIDES; i++) {
            int quadrant = (i + 4) / PER_QUADRANT % 4;
            int across = (i + 4) % PER_QUADRANT;
            if (across >= 2 && across < 6 && (windows & 1 << quadrant) != 0) continue;
            float u0 = ARC[i] / 16, u1 = ARC[i + 1] / 16;
            float tile = (float) Math.floor(u0);
            Point a = lower.at(i), b = lower.at(i + 1), A = upper.at(i), B = upper.at(i + 1);
            if (u1 > tile + 1) {
                float t = (tile + 1 - u0) / (u1 - u0);
                Point seam = a.lerp(b, t), upperSeam = A.lerp(B, t);
                strip(vc, pose, sprite, a, seam, A, upperSeam, bottom, top, u0 - tile, 1, v0, v1,
                    lower.slope(), upper.slope(), bottomNormal, topNormal, inset, color, light, overlay);
                a = seam;
                A = upperSeam;
                u0 = ++tile;
            }
            strip(vc, pose, sprite, a, b, A, B, bottom, top, u0 - tile, u1 - tile, v0, v1,
                lower.slope(), upper.slope(), bottomNormal, topNormal, inset, color, light, overlay);
        }
    }

    // Texture seams are measured on the upright wall and carried down the bowl.
    private static float[] arc() {
        var distances = new float[SIDES + 1];
        double edge = Math.asin(WINDOW_HALF / LiddedVesselBlock.BORE_RADIUS);
        for (int i = 0; i <= SIDES; i++)
            distances[i] = (float) (((i + 4) / PER_QUADRANT * Math.PI / 2 + windowAngle(edge, (i + 4) % PER_QUADRANT - 4)) * LiddedVesselBlock.BORE_RADIUS);
        return distances;
    }

    private static void strip(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, Point a, Point b, Point A, Point B,
            float bottom, float top, float u0, float u1, float v0, float v1, float lowerSlope, float upperSlope,
            float lowerNormal, float upperNormal, float inset, int color, int light, int overlay) {
        if (lowerNormal > 0) {
            Point swap = a; a = b; b = swap;
            swap = A; A = B; B = swap;
            float u = u0; u0 = u1; u1 = u;
        }
        vertex(vc, pose, sprite, A, top, u0, v0, A.nx() * upperNormal, -upperSlope * upperNormal, A.nz() * upperNormal, inset, color, light, overlay);
        vertex(vc, pose, sprite, a, bottom, u0, v1, a.nx() * lowerNormal, -lowerSlope * lowerNormal, a.nz() * lowerNormal, inset, color, light, overlay);
        vertex(vc, pose, sprite, b, bottom, u1, v1, b.nx() * lowerNormal, -lowerSlope * lowerNormal, b.nz() * lowerNormal, inset, color, light, overlay);
        vertex(vc, pose, sprite, B, top, u1, v0, B.nx() * upperNormal, -upperSlope * upperNormal, B.nz() * upperNormal, inset, color, light, overlay);
    }

    private static void cap(VertexConsumer vc, PoseStack ms, Ring ring, float y, TextureAtlasSprite sprite, int normal, int color, int light, int overlay) {
        var pose = ms.last();
        Point centre = Point.CENTRE;
        for (int i = 0; i < SIDES; i++) {
            flat(vc, pose, sprite, centre, y, normal, color, light, overlay);
            flat(vc, pose, sprite, ring.at(normal > 0 ? i + 1 : i).inset(FLUID_INSET), y, normal, color, light, overlay);
            flat(vc, pose, sprite, ring.at(normal > 0 ? i : i + 1).inset(FLUID_INSET), y, normal, color, light, overlay);
            flat(vc, pose, sprite, centre, y, normal, color, light, overlay);
        }
    }

    private static void ledge(VertexConsumer vc, PoseStack ms, Ring ring, float y, int first, Direction face, int normal, TextureAtlasSprite sprite, int light, int overlay) {
        var pose = ms.last();
        for (int i = first; i < first + 4; i++) {
            Point a = ring.at(normal > 0 ? i : i + 1), b = ring.at(normal > 0 ? i + 1 : i);
            flat(vc, pose, sprite, a, y, normal, -1, light, overlay);
            flat(vc, pose, sprite, b, y, normal, -1, light, overlay);
            flat(vc, pose, sprite, b.toShell(face), y, normal, -1, light, overlay);
            flat(vc, pose, sprite, a.toShell(face), y, normal, -1, light, overlay);
        }
    }

    private static void jamb(VertexConsumer vc, PoseStack ms, Ring lower, Ring upper, float bottom, float top, int edge,
            Direction face, int inward, TextureAtlasSprite sprite, int light, int overlay) {
        var pose = ms.last();
        Point a = lower.at(edge), A = upper.at(edge), b = a.toShell(face), B = A.toShell(face);
        int nx = -face.getStepZ() * inward, nz = face.getStepX() * inward;
        if (inward < 0) {
            Point swap = a; a = b; b = swap;
            swap = A; A = B; B = swap;
        }
        float v0 = ((float) Math.floor(bottom / 16) * 16 + 16 - top) / 16, v1 = v0 + (top - bottom) / 16;
        vertex(vc, pose, sprite, A, top, tunnelU(A, face), v0, nx, 0, nz, 0, -1, light, overlay);
        vertex(vc, pose, sprite, a, bottom, tunnelU(a, face), v1, nx, 0, nz, 0, -1, light, overlay);
        vertex(vc, pose, sprite, b, bottom, tunnelU(b, face), v1, nx, 0, nz, 0, -1, light, overlay);
        vertex(vc, pose, sprite, B, top, tunnelU(B, face), v0, nx, 0, nz, 0, -1, light, overlay);
    }

    private static float tunnelU(Point point, Direction face) {
        return (SHELL_RADIUS - point.x() * face.getStepX() - point.z() * face.getStepZ()) / 20;
    }

    private static void flat(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, Point point, float y,
            int normal, int color, int light, int overlay) {
        vertex(vc, pose, sprite, point, y, .5f + point.x() / 48, .5f + point.z() / 48, 0, normal, 0, 0, color, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite, Point point, float y, float u, float v,
            float nx, float ny, float nz, float inset, int color, int light, int overlay) {
        vc.addVertex(pose, (CENTRE + point.x() - point.nx() * inset) / 16, y / 16, (CENTRE + point.z() - point.nz() * inset) / 16)
            .setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24)
            .setUv(sprite.getU(u), sprite.getV(v)).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private record Ring(float y, Point[] points, float slope) {
        Point at(int index) { return points[Math.floorMod(index, SIDES)]; }
    }

    private record Point(float x, float z, float nx, float nz) {
        static final Point CENTRE = new Point(0, 0, 0, 0);
        Point lerp(Point other, float t) { return new Point(mix(x, other.x, t), mix(z, other.z, t), mix(nx, other.nx, t), mix(nz, other.nz, t)); }
        Point toShell(Direction face) { return face.getAxis() == Direction.Axis.X ? new Point(SHELL_RADIUS * face.getStepX(), z, 0, 0) : new Point(x, SHELL_RADIUS * face.getStepZ(), 0, 0); }
        Point toSquare(float radius) { float scale = radius / Math.max(Math.abs(x), Math.abs(z)); return new Point(x * scale, z * scale, 0, 0); }
        Point inset(float amount) { return new Point(x - nx * amount, z - nz * amount, nx, nz); }
    }
}
