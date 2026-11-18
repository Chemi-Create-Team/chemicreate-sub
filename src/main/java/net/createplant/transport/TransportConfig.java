package net.createplant.transport;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned grade compatibility and released-gas settings, synchronized by NeoForge. */
public final class TransportConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GAS_EXPLOSIONS;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("released_gas");
        GAS_EXPLOSIONS=b.comment("Allow released flammable gas to ignite and explode.").define("flammableGasExplosions",true);
        b.pop(); SPEC = b.build();
    }
    private TransportConfig() { }
}
