package net.createplant.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GameTestServer exits the disposable process with its result, so its normal world-save shutdown only adds blocking I/O. */
@Mixin(value = MinecraftServer.class, remap = false)
public class GameTestShutdownMixin {
    @Inject(method = "stopServer", at = @At("HEAD"), cancellable = true)
    private void createPlant$skipDisposableWorldShutdown(CallbackInfo ci) {
        if ((Object) this instanceof GameTestServer) ci.cancel();
    }
}
