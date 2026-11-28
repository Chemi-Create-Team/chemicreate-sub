package net.createplant.atomics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.registry.CENuclear;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Draws the four long fuel assemblies and the continuous 7 + 1.25 + 4.5 metre control rods of a channel bundle. */
public class FuelChannelRenderer extends SafeBlockEntityRenderer<FuelChannelBlockEntity> {
    public static final PartialModel TUBE = PartialModel.of(CreatePlant.location("block/rbmk/fuel_rod_tube"));
    public static final PartialModel SHIELD_PLUG = PartialModel.of(CreatePlant.location("block/rbmk/fuel_rod_shield_plug"));
    public static final PartialModel ABSORBER = PartialModel.of(CreatePlant.location("block/rbmk/control_rod_absorber"));
    public static final PartialModel CONNECTOR = PartialModel.of(CreatePlant.location("block/rbmk/control_rod_connector"));
    public static final PartialModel DISPLACER = PartialModel.of(CreatePlant.location("block/rbmk/control_rod_displacer"));

    public FuelChannelRenderer(BlockEntityRendererProvider.Context context) { }

    public static void init() { }

    @Override protected void renderSafe(FuelChannelBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null || be.controllerPos() == null || !be.top()) return;
        var column = be.bottom();
        if (column == null) return;
        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());
        var layout = column.layout();
        for (int y = 1; y <= layout.upperStructureHeight(); y++) {
            if (be.getLevel().getBlockState(be.getBlockPos().above(y)).is(CENuclear.FUEL_CHANNEL_TUBE.get())) continue;
            int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(y));
            PartialModel part = y <= layout.upperShieldHeight() ? SHIELD_PLUG : TUBE;
            for (int i = 0; i < FuelChannelBlockEntity.HOLES.length; i++) if (column.loaded(i))
                CachedBuffers.partial(part, be.getBlockState()).translate(FuelChannelBlockEntity.HOLES[i][0], y, FuelChannelBlockEntity.HOLES[i][1])
                    .light(lit).renderInto(ms, vb);
        }
        var core = be.formedCore();
        for (int i = 0; i < FuelChannelBlockEntity.HOLES.length; i++) if (column.controlInstalled(i)) {
            double depth = core == null ? column.controlDepth(i) : core.controlDepth(column.getBlockPos(), i);
            double displacer = 1 - layout.connectorLength() - layout.displacerLength() - depth;
            segment(be, ms, vb, DISPLACER, i, displacer, layout.displacerLength());
            segment(be, ms, vb, CONNECTOR, i, displacer + layout.displacerLength(), layout.connectorLength());
            segment(be, ms, vb, ABSORBER, i, 1 - depth, layout.absorberLength());
        }
    }

    private static void segment(FuelChannelBlockEntity be, PoseStack ms, VertexConsumer vb, PartialModel part, int slot, double bottom, double length) {
        for (double y = 0; y < length - 1e-6; y += 1) {
            float piece = (float) Math.min(1, length - y);
            int lit = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().offset(0, (int) Math.floor(bottom + y), 0));
            CachedBuffers.partial(part, be.getBlockState()).translate(FuelChannelBlockEntity.HOLES[slot][0], bottom + y, FuelChannelBlockEntity.HOLES[slot][1])
                .scaleY(piece).light(lit).renderInto(ms, vb);
        }
    }

    @Override public boolean shouldRenderOffScreen(FuelChannelBlockEntity be) { return true; }

    @Override public int getViewDistance() { return 128; }
}
