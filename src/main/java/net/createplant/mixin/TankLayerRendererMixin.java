package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.fluids.tank.FluidTankRenderer;
import java.util.List;
import net.createmod.catnip.render.FluidRenderHelper;
import net.createplant.client.TankLayerColors;
import net.createplant.mixture.MixtureHelper;
import net.createplant.thermal.TankLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = FluidTankRenderer.class, remap = false)
public class TankLayerRendererMixin {
    @WrapOperation(method = "renderSafe(Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/render/FluidRenderHelper;renderFluidBox(Ljava/lang/Object;FFFFFFLnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/PoseStack;IZZ)V"))
    private void createPlant$drawLiquidLayers(FluidRenderHelper<?> helper, Object stack, float x0, float y0, float z0, float x1, float y1, float z1,
        MultiBufferSource buffers, PoseStack poses, int light, boolean flowing, boolean shade, Operation<Void> original) {
        if (!(stack instanceof FluidStack fluid) || !MixtureHelper.isMixture(fluid)) {
            original.call(helper, stack, x0, y0, z0, x1, y1, z1, buffers, poses, light, flowing, shade);
            return;
        }
        float surface = y0;
        var layers = TankLayers.ordered(List.of(fluid));
        for (int i = 0; i < layers.size(); i++) {
            var layer = layers.get(i);
            float top = surface + (y1 - y0) * layer.getAmount() / fluid.getAmount();
            int index = i;
            MultiBufferSource colored = type -> TankLayerColors.tint(buffers.getBuffer(type), layer, index, layers.size() > 1);
            original.call(helper, TankLayerColors.surface(layer), x0, surface, z0, x1, top, z1, colored, poses, light, flowing, shade);
            surface = top;
        }
    }
}
