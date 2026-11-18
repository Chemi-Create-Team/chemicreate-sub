package net.createplant.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Nixie glass that blends like block glass but writes no depth, so the digit standing inside the tube shows through it whichever
 * is drawn first. Extends RenderType only to reach its protected state shards.
 */
final class NixieGlassRenderType extends RenderType {
    static final RenderType GLASS = create("create_refinery_n_reactors_nixie_glass", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, true,
        CompositeState.builder()
            .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false));

    private NixieGlassRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
        Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
