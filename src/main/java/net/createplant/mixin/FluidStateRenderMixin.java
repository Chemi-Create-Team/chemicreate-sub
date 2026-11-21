package net.createplant.mixin;

import net.createmod.catnip.platform.NeoForgeFluidHelper;
import net.createplant.client.FluidStateVisuals;
import net.createplant.thermal.FluidState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=NeoForgeFluidHelper.class,remap=false)
public class FluidStateRenderMixin {
    @Inject(method="getColor(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
        at=@At("RETURN"),cancellable=true)
    private void createPlant$tint(FluidStack stack,BlockAndTintGetter level,BlockPos pos,CallbackInfoReturnable<Integer> ci) {
        if(!FluidStateVisuals.hasRenderingExtension(stack))ci.setReturnValue(FluidStateVisuals.tint(stack,ci.getReturnValueI()));
    }

    @Inject(method="getStillTexture(Lnet/neoforged/neoforge/fluids/FluidStack;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
        at=@At("HEAD"),cancellable=true)
    private void createPlant$vapour(FluidStack stack,CallbackInfoReturnable<TextureAtlasSprite> ci) {
        if(FluidState.gas(stack))ci.setReturnValue(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FluidStateVisuals.VAPOUR));
    }

    @Inject(method="getLuminosity(Lnet/neoforged/neoforge/fluids/FluidStack;)I",at=@At("RETURN"),cancellable=true)
    private void createPlant$glow(FluidStack stack,CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(FluidStateVisuals.luminosity(stack,ci.getReturnValueI()));
    }
}
