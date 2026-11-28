package net.createplant.atomics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.atomics.block.entity.FuelChannelBlockEntity;
import net.createplant.atomics.block.entity.RodCapBlockEntity;
import net.createplant.atomics.nuclear.ReactorPhysics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Independently offsets the four existing pressure-channel cap partials; the static block model and textures stay untouched. */
public class RBMKPlugRenderer extends SmartBlockEntityRenderer<RodCapBlockEntity> {
    private static final PartialModel FUEL = PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_fuel"));
    private static final PartialModel[] CONTROL = {
        PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_control")),
        PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_control_green")),
        PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_control_blue")),
        PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_control_yellow"))
    };
    private static final PartialModel CONTROL_EMPTY = PartialModel.of(CreatePlant.location("block/rbmk/channel_cap_control_empty"));

    public RBMKPlugRenderer(BlockEntityRendererProvider.Context context) { super(context); }

    public static void init() { }

    @Override protected void renderSafe(RodCapBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        var column = be.plugColumn();
        if (column == null) return;
        var vb = buffer.getBuffer(RenderType.cutout());
        int capLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        for (int i = 0; i < FuelChannelBlockEntity.HOLES.length; i++) if (column.active(i)) {
            PartialModel model = !column.control(i) ? FUEL : column.controlInstalled(i) ? controlModel(be, i) : CONTROL_EMPTY;
            float[] hole = FuelChannelBlockEntity.HOLES[i];
            ms.pushPose();
            ms.translate(hole[0], be.plugOffset(i, partialTicks), hole[1]);
            CachedBuffers.partial(model, be.getBlockState()).light(capLight).renderInto(ms, vb);
            ms.popPose();
        }
    }

    /** Stable maintenance colours break up the control positions like the identification plates on an RBMK operating floor. */
    private static PartialModel controlModel(RodCapBlockEntity be, int slot) {
        int x = be.getBlockPos().getX() * ReactorPhysics.CHANNEL_GRID + slot % ReactorPhysics.CHANNEL_GRID;
        int z = be.getBlockPos().getZ() * ReactorPhysics.CHANNEL_GRID + slot / ReactorPhysics.CHANNEL_GRID;
        return CONTROL[Math.floorMod(x * 31 + z * 17 + x * z * 7, CONTROL.length)];
    }
}
