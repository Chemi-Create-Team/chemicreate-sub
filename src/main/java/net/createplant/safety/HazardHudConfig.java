package net.createplant.safety;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HazardHudConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the chemical hazard helmet visor and gas indicator in first person.")
        .define("hazardHelmetOverlay", true);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private HazardHudConfig() { }
}
