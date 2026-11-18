package net.createplant.mixin;

import net.createplant.nuclear.RadiationSources;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stamp physical storage insertions, so immediate saving or chunk unloading cannot reset cooling
 * history.
 */
@Mixin(value = ItemStackHandler.class, remap = false)
public class RadiationItemStorageMixin {
    @Unique
    private void radiationStamp(int slot) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread())
            RadiationSources.stamp(
                    ((ItemStackHandler) (Object) this).getStackInSlot(slot), server.overworld());
    }

    @Inject(method = "setStackInSlot", at = @At("RETURN"))
    private void stored(int slot, ItemStack stack, CallbackInfo ci) {
        radiationStamp(slot);
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void inserted(
            int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> ci) {
        if (!simulate) radiationStamp(slot);
    }
}
