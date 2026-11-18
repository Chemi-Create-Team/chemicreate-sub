package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The chemical sprayer with the potato cannon's own cog round its neck. The cog turns while the sprayer is held down and
 * stops where it is when let go.
 */
public class SprayerItemRenderer extends CustomRenderedItemModelRenderer {
    public static final PartialModel COG = PartialModel.of(ResourceLocation.fromNamespaceAndPath("create", "item/potato_cannon/cog"));
    /** Degrees a tick while spraying; the cog keeps the angle it reached. */
    private static final float SPIN = 24;
    private static float angle, last = Float.NaN;

    /** Loads the cog with the other partial models. */
    public static void init() { }

    @Override protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer, ItemDisplayContext transform, PoseStack ms,
        MultiBufferSource buffer, int light, int overlay) {
        renderer.render(model.getOriginalModel(), light);
        var player = Minecraft.getInstance().player;
        float now = AnimationTickHolder.getRenderTime();
        if (player != null && player.isUsingItem() && player.getUseItem() == stack && !Float.isNaN(last)) angle = (angle + (now - last) * SPIN) % 360;
        last = now;
        // The cog's axis runs along the barrel, half a pixel above the model's centre.
        ms.pushPose();
        ms.translate(0, 1 / 32f, 0);
        ms.mulPose(Axis.ZP.rotationDegrees(angle));
        ms.translate(0, -1 / 32f, 0);
        renderer.render(COG.get(), light);
        ms.popPose();
    }

    /** The baked sprayer, and its spraying override, drawn by this renderer. */
    public static final class Model extends CustomRenderedItemModel {
        private final ItemOverrides overrides;
        private CustomRenderedItemModel spraying;

        public Model(BakedModel original) {
            super(original);
            var inner = original.getOverrides();
            overrides = new ItemOverrides() {
                @Override public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                    var resolved = inner.resolve(original, stack, level, entity, seed);
                    if (resolved == null || resolved == original) return Model.this;
                    if (spraying == null || spraying.getOriginalModel() != resolved) spraying = new CustomRenderedItemModel(resolved);
                    return spraying;
                }
            };
        }

        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
