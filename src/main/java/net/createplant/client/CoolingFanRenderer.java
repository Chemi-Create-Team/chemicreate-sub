package net.createplant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createplant.CreatePlant;
import net.createplant.block.entity.CoolingFanBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Hub, guard and ring stay in the chunk model; only the 3x3 rotor and its drive shaft turn with the kinetic network.
 * Model elements tilt about one axis only, so the four pitched blades are drawn twice, 45 degrees apart, for eight. */
public class CoolingFanRenderer extends KineticBlockEntityRenderer<CoolingFanBlockEntity> {
    public static final PartialModel ROTOR = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, "block/cooling_fan/rotor"));
    public static final PartialModel BLADES = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreatePlant.MOD_ID, "block/cooling_fan/blades"));
    public CoolingFanRenderer(BlockEntityRendererProvider.Context context) { super(context); }
    /** Partial models must exist before the first model bake. */
    public static void init() { }
    @Override protected void renderSafe(CoolingFanBlockEntity be, float partialTicks, PoseStack pose,
        MultiBufferSource buffers, int light, int overlay) {
        var buffer = buffers.getBuffer(RenderType.solid());
        float angle = getAngleForBe(be, be.getBlockPos(), Direction.Axis.Y);
        renderRotatingBuffer(be, CachedBuffers.partial(ROTOR, be.getBlockState()), pose, buffer, light);
        for (int set = 0; set < 2; set++)
            kineticRotationTransform(CachedBuffers.partial(BLADES, be.getBlockState()), be, Direction.Axis.Y, angle + set * Mth.PI / 4, light).renderInto(pose, buffer);
    }
    @Override public boolean shouldRenderOffScreen(CoolingFanBlockEntity be) { return true; }
}
