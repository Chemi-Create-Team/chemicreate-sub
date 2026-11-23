package net.createplant.client;

import java.lang.reflect.Method;

/**
 * Whether a shader pack (Iris or a fork of it) is drawing the world. A pack replaces the game's own programs, so a custom core shader such as the
 * ground mist's draws nothing or draws wrongly under it; the mist then falls back to billboards on vanilla programs, which every pack handles.
 * Read through reflection so that the mod needs neither Iris to run nor to build.
 */
final class ShaderPackCompat {
    private static Object api;
    private static Method inUse;
    private static boolean looked;

    private ShaderPackCompat() { }

    static boolean active() {
        if (!looked) {
            looked = true;
            for (var name : new String[] {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    var type = Class.forName(name);
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                    break;
                } catch (ReflectiveOperationException | LinkageError absent) { }
            }
        }
        try {
            return inUse != null && (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }
}
