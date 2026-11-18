package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.utility.DyeHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;

/**
 * A bank of hollow Create nixie tubes standing side by side in a recess of a panel, one character per tube, filling the recess with
 * no gap between tubes. Each tube is a glass box with a socket at its foot, textured from Create's nixie tube, and the glowing digit
 * stands inside the glass in Create's two nixie colours. Coordinates are panel pixels: {@code u} from the operator's left, {@code y}
 * from the bottom, and depth from {@code face}, the glass over the recess, inwards.
 */
final class NixieStrip {
    private static final ResourceLocation TUBE = ResourceLocation.fromNamespaceAndPath("create", "block/nixie_tube");
    /** A tube is at most this wide for its height, like Create's own 6 x 9 pixel tubes. */
    private static final float ASPECT = 6 / 9f;
    /** Create's digits fill about 80% of the tube height. */
    private static final float GLYPH_FILL = .8f;
    private static final float MARGIN = .15f, SOCKET = .5f;
    /** Create's pale glass dimmed, so that rows of tubes in a dark recess keep the recess dark and only catch a faint sheen. */
    private static final int GLASS = 0xFF484C4E, GLASS_SIDE = 0x80484C4E;
    private static TextureAtlasSprite sprite;

    private NixieStrip() {}

    /**
     * Fills the recess [u0, u1] x [y0, y1], {@code depth} pixels deep behind {@code face}, with tubes and centres {@code text} in them;
     * the spare tubes and spaces stay unlit.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, String text, float u0, float u1, float y0, float y1, float face, float depth,
        DyeColor colour, int light) {
        int length = text.codePointCount(0, text.length());
        float height = y1 - y0 - 2 * MARGIN, span = u1 - u0 - 2 * MARGIN;
        int count = Math.max(length, (int) (span / (height * ASPECT)));
        if (count == 0)
            return;
        float width = span / count, start = u0 + MARGIN, base = y0 + MARGIN, top = base + height, glass = base + SOCKET;
        float front = face + MARGIN, back = face + depth - MARGIN;
        if (sprite == null)
            sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TUBE);
        VertexConsumer solid = buffers.getBuffer(RenderType.cutout());
        for (int i = 0; i < count; i++) {
            float a = start + i * width, b = a + width;
            front(pose, solid, a, base, b, glass, front, 0, 10, 6, 13, -1, light);
            up(pose, solid, a, b, glass, front, back, 6, 6, 12, 12, -1, light);
        }
        VertexConsumer glassBuffer = buffers.getBuffer(NixieGlassRenderType.GLASS);
        for (int i = 0; i < count; i++) {
            float a = start + i * width, b = a + width;
            front(pose, glassBuffer, a, glass, b, top, front, 0, 1, 6, 10, GLASS, light);
            up(pose, glassBuffer, a, b, top, front, back, 6, 0, 12, 6, GLASS, light);
            side(pose, glassBuffer, b, glass, top, front, back, light);
        }
        side(pose, glassBuffer, start, glass, top, front, back, light);
        var font = Minecraft.getInstance().font;
        var colours = DyeHelper.getDyeColors(colour);
        int core = Color.mixColors(colours.getFirst(), colours.getSecond(), .1f), glow = colours.getSecond();
        int first = (count - length) / 2, index = 0;
        // The digit stands just behind the front glass, so it stays centred in small tubes seen from above or below.
        float middle = front + .4f, centreY = (glass + top) / 2;
        for (int offset = 0; offset < text.length(); index++) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == ' ')
                continue;
            String glyph = new String(Character.toChars(codePoint));
            float glyphWidth = Math.max(1, font.width(glyph));
            float scale = Math.min((top - glass) * GLYPH_FILL / 9, width * .85f / glyphWidth) / 16;
            float centre = start + (first + index + .5f) * width;
            pose.pushPose();
            // The font's line box starts above the glyph body; this lifts the body to the middle of the glass.
            pose.translate(1 - centre / 16, centreY / 16 + 4.5f * scale, middle / 16);
            pose.scale(-scale, -scale, scale);
            font.drawInBatch(glyph, -glyphWidth / 2, 0, core, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.translate(-.35f, .35f, .5f);
            font.drawInBatch(glyph, -glyphWidth / 2, 0, glow, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    private static float u(float texel) { return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * texel / 16; }
    private static float v(float texel) { return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * texel / 16; }

    /** The face towards the operator, between panel pixels, textured from a region of Create's 16 px nixie texture. */
    private static void front(PoseStack pose, VertexConsumer buffer, float u0, float y0, float u1, float y1, float z, float tu0, float tv0, float tu1,
        float tv1, int colour, int light) {
        var last = pose.last();
        float x0 = 1 - u0 / 16, x1 = 1 - u1 / 16, b = y0 / 16, t = y1 / 16, d = z / 16;
        buffer.addVertex(last, x0, b, d).setColor(colour).setUv(u(tu0), v(tv1)).setLight(light).setNormal(last, 0, 0, -1);
        buffer.addVertex(last, x1, b, d).setColor(colour).setUv(u(tu1), v(tv1)).setLight(light).setNormal(last, 0, 0, -1);
        buffer.addVertex(last, x1, t, d).setColor(colour).setUv(u(tu1), v(tv0)).setLight(light).setNormal(last, 0, 0, -1);
        buffer.addVertex(last, x0, t, d).setColor(colour).setUv(u(tu0), v(tv0)).setLight(light).setNormal(last, 0, 0, -1);
    }

    /** The upward face of a tube or socket, from the front of the recess to its back. */
    private static void up(PoseStack pose, VertexConsumer buffer, float u0, float u1, float y, float z0, float z1, float tu0, float tv0, float tu1,
        float tv1, int colour, int light) {
        var last = pose.last();
        float x0 = 1 - u0 / 16, x1 = 1 - u1 / 16, h = y / 16, f = z0 / 16, k = z1 / 16;
        buffer.addVertex(last, x0, h, f).setColor(colour).setUv(u(tu0), v(tv1)).setLight(light).setNormal(last, 0, 1, 0);
        buffer.addVertex(last, x1, h, f).setColor(colour).setUv(u(tu1), v(tv1)).setLight(light).setNormal(last, 0, 1, 0);
        buffer.addVertex(last, x1, h, k).setColor(colour).setUv(u(tu1), v(tv0)).setLight(light).setNormal(last, 0, 1, 0);
        buffer.addVertex(last, x0, h, k).setColor(colour).setUv(u(tu0), v(tv0)).setLight(light).setNormal(last, 0, 1, 0);
    }

    /** A glass wall between neighbouring tubes, drawn faint because it is seen edge-on through the front glass. */
    private static void side(PoseStack pose, VertexConsumer buffer, float u, float y0, float y1, float z0, float z1, int light) {
        var last = pose.last();
        float x = 1 - u / 16, b = y0 / 16, t = y1 / 16, f = z0 / 16, k = z1 / 16;
        buffer.addVertex(last, x, b, f).setColor(GLASS_SIDE).setUv(u(0), v(10)).setLight(light).setNormal(last, -1, 0, 0);
        buffer.addVertex(last, x, b, k).setColor(GLASS_SIDE).setUv(u(6), v(10)).setLight(light).setNormal(last, -1, 0, 0);
        buffer.addVertex(last, x, t, k).setColor(GLASS_SIDE).setUv(u(6), v(1)).setLight(light).setNormal(last, -1, 0, 0);
        buffer.addVertex(last, x, t, f).setColor(GLASS_SIDE).setUv(u(0), v(1)).setLight(light).setNormal(last, -1, 0, 0);
    }
}
