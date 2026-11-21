package net.createplant.mixin;

import net.createplant.thermal.FluidIdentity;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=FluidStack.class,remap=false)
public class FluidStateStackMixin {
    @Shadow @Final @Mutable private Fluid fluid;

    @Inject(method="<init>(Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",at=@At("RETURN"))
    private void createPlant$migrate(Fluid registered,int amount,PatchedDataComponentMap components,CallbackInfo ci) {
        var stack=(FluidStack)(Object)this;
        fluid=FluidIdentity.migrate(stack,registered);
        net.createplant.mixture.MixtureHelper.migrateContents(stack);
    }

    @Inject(method="is(Lnet/minecraft/world/level/material/Fluid;)Z",at=@At("RETURN"),cancellable=true)
    private void createPlant$legacyFluid(Fluid requested,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValueZ() && FluidIdentity.matches((FluidStack)(Object)this,requested))cir.setReturnValue(true);
    }

    @Inject(method="is(Lnet/minecraft/tags/TagKey;)Z",at=@At("RETURN"),cancellable=true)
    private void createPlant$legacyTag(TagKey<Fluid> requested,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValueZ() && FluidIdentity.matchesVariant((FluidStack)(Object)this,variant -> variant.builtInRegistryHolder().is(requested)))
            cir.setReturnValue(true);
    }

    @Inject(method="is(Lnet/minecraft/core/HolderSet;)Z",at=@At("RETURN"),cancellable=true)
    private void createPlant$legacySet(HolderSet<Fluid> requested,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValueZ() && FluidIdentity.matchesVariant((FluidStack)(Object)this,variant -> requested.contains(variant.builtInRegistryHolder())))
            cir.setReturnValue(true);
    }
}
