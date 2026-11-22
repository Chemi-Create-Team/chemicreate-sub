package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.fluid.FluidRenderer;
import net.createplant.client.FluidStateVisuals;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=FluidRenderer.class,remap=false)
public class FluidStateStreamRenderMixin {
    private static final String STREAM="renderFluidStream(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/minecraft/core/Direction;FFZLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack;I)V";

    @WrapOperation(method=STREAM,at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getFlowingTexture(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation createPlant$flowTexture(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<ResourceLocation> original) {
        return FluidStateVisuals.texture(stack,original.call(extensions,stack));
    }

    @WrapOperation(method=STREAM,at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getStillTexture(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation createPlant$stillTexture(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<ResourceLocation> original) {
        return FluidStateVisuals.texture(stack,original.call(extensions,stack));
    }

    @WrapOperation(method=STREAM,at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor(Lnet/neoforged/neoforge/fluids/FluidStack;)I"))
    private static int createPlant$flowColor(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<Integer> original) {
        int base=original.call(extensions,stack);
        return FluidStateVisuals.hasRenderingExtension(stack) ? base : FluidStateVisuals.tint(stack,base);
    }
}
