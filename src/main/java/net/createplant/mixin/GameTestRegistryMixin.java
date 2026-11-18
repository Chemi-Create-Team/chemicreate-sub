package net.createplant.mixin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import net.createplant.gametest.ExplicitGameTest;
import net.minecraft.gametest.framework.GameTestRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Allows the opt-in developer run to register one GameTest class without weakening the normal full test server. */
@Mixin(value = GameTestRegistry.class, remap = false)
public class GameTestRegistryMixin {
    @Inject(method = "register(Ljava/lang/reflect/Method;Ljava/util/Set;)V", at = @At("HEAD"), cancellable = true)
    private static void createPlant$filterDeveloperRun(Method method, Set<String> namespaces, CallbackInfo ci) {
        String requested = System.getProperty("createplant.gameTestClasses");
        if (requested == null || requested.isBlank()) {
            if (method.getDeclaringClass().isAnnotationPresent(ExplicitGameTest.class)) ci.cancel();
            return;
        }
        String simple = method.getDeclaringClass().getSimpleName();
        String qualified = method.getDeclaringClass().getName();
        if (Arrays.stream(requested.split(",")).map(String::trim)
            .noneMatch(name -> name.equals(simple) || name.equals(qualified))) ci.cancel();
    }
}
