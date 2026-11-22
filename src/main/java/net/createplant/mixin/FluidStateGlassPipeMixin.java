package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.pipes.GlassPipeVisual;
import net.createplant.client.FluidStateVisuals;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=GlassPipeVisual.class,remap=false)
public class FluidStateGlassPipeMixin {
    @WrapOperation(method="beginFrame",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getFlowingTexture(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation createPlant$flowTexture(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<ResourceLocation> original) {
        return FluidStateVisuals.texture(stack,original.call(extensions,stack));
    }

    @WrapOperation(method="beginFrame",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getStillTexture(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation createPlant$stillTexture(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<ResourceLocation> original) {
        return FluidStateVisuals.texture(stack,original.call(extensions,stack));
    }

    @WrapOperation(method="beginFrame",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor(Lnet/neoforged/neoforge/fluids/FluidStack;)I"))
    private int createPlant$flowColor(IClientFluidTypeExtensions extensions,FluidStack stack,Operation<Integer> original) {
        int base=original.call(extensions,stack);
        return FluidStateVisuals.hasRenderingExtension(stack) ? base : FluidStateVisuals.tint(stack,base);
    }
}
