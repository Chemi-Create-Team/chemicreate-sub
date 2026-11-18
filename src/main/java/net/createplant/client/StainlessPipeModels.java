package net.createplant.client;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.fluids.PipeAttachmentModel;
import com.simibubi.create.content.fluids.pipes.EncasedPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassPipeVisual;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.createplant.CreatePlant;
import net.createplant.registry.CETransport;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The stainless steel pipes render through Create's own pipe models and attachment wrapper, exactly as Create's copper pipes do;
 * this only swaps Create's pipe sprites for the stainless ones, including the rims, drains and brackets Create adds at runtime.
 */
public final class StainlessPipeModels {
    private static final String[] SPRITES = {"pipes", "pipes_connected", "glass_fluid_pipe", "encased_pipe"};

    private StainlessPipeModels() { }

    /** Create's glass pipe layer, casing join and fluid visual, registered for the stainless variants as Create registers its own. */
    @SuppressWarnings("deprecation")
    public static void setup() {
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(CETransport.STAINLESS_GLASS_PIPE.get(), RenderType.cutoutMipped());
        CreateClient.CASING_CONNECTIVITY.make(CETransport.STAINLESS_ENCASED_PIPE.get(), AllSpriteShifts.COPPER_CASING,
            (state, face) -> !state.getValue(EncasedPipeBlock.FACING_TO_PROPERTY_MAP.get(face)));
        SimpleBlockEntityVisualizer.builder(CETransport.STAINLESS_GLASS_PIPE_ENTITY.get())
            .factory(GlassPipeVisual::new)
            .skipVanillaRender(be -> true)
            .apply();
    }

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Function<Material, TextureAtlasSprite> sprites = event.getTextureGetter();
        Map<TextureAtlasSprite, TextureAtlasSprite> swap = new IdentityHashMap<>();
        for (String name : SPRITES)
            swap.put(sprite(sprites, ResourceLocation.fromNamespaceAndPath("create", "block/" + name)), sprite(sprites, CreatePlant.location("block/transport/stainless_steel_" + name)));
        var particle = sprite(sprites, CreatePlant.location("block/transport/stainless_steel_pipes"));
        Map<BakedQuad, BakedQuad> cache = new ConcurrentHashMap<>();
        var casing = new EncasedCTBehaviour(AllSpriteShifts.COPPER_CASING);
        wrap(event, CETransport.STAINLESS_PIPE.get(), model -> new Retextured(PipeAttachmentModel.withAO(model), swap, particle, cache));
        wrap(event, CETransport.STAINLESS_GLASS_PIPE.get(), model -> new Retextured(PipeAttachmentModel.withAO(model), swap, particle, cache));
        wrap(event, CETransport.STAINLESS_ENCASED_PIPE.get(),
            model -> new Retextured(PipeAttachmentModel.withAO(new CTModel(model, casing)), swap, particle, cache));
    }

    private static TextureAtlasSprite sprite(Function<Material, TextureAtlasSprite> sprites, ResourceLocation location) {
        return sprites.apply(new Material(InventoryMenu.BLOCK_ATLAS, location));
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, Function<BakedModel, BakedModel> wrapper) {
        for (var state : block.getStateDefinition().getPossibleStates())
            event.getModels().computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, model) -> wrapper.apply(model));
    }

    /** Swapped quads are cached by identity: Create's pipe and attachment quads are baked once, so the cache stays small. */
    private static final class Retextured extends BakedModelWrapper<BakedModel> {
        private final Map<TextureAtlasSprite, TextureAtlasSprite> swap;
        private final TextureAtlasSprite particle;
        private final Map<BakedQuad, BakedQuad> cache;

        private Retextured(BakedModel model, Map<TextureAtlasSprite, TextureAtlasSprite> swap, TextureAtlasSprite particle, Map<BakedQuad, BakedQuad> cache) {
            super(model);
            this.swap = swap;
            this.particle = particle;
            this.cache = cache;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
            List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
            List<BakedQuad> out = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                TextureAtlasSprite target = swap.get(quad.getSprite());
                if (target == null)
                    continue;
                if (out == null)
                    out = new ArrayList<>(quads);
                out.set(i, cache.computeIfAbsent(quad, original -> retexture(original, target)));
            }
            return out == null ? quads : out;
        }

        private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite target) {
            TextureAtlasSprite source = quad.getSprite();
            int[] vertices = quad.getVertices().clone();
            for (int vertex = 0; vertex < 4; vertex++) {
                float u = (BakedQuadHelper.getU(vertices, vertex) - source.getU0()) / (source.getU1() - source.getU0());
                float v = (BakedQuadHelper.getV(vertices, vertex) - source.getV0()) / (source.getV1() - source.getV0());
                BakedQuadHelper.setU(vertices, vertex, target.getU(u));
                BakedQuadHelper.setV(vertices, vertex, target.getV(v));
            }
            return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), target, quad.isShade(), quad.hasAmbientOcclusion());
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return particle;
        }
    }
}
