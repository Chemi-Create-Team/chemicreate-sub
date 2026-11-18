package net.createplant.compat.deepseas;

import java.util.Map;
import net.createplant.CreatePlant;

/**
 * The pressure hulls of Create: Deep Seas submarines. Base depth is the water depth in blocks a hull block holds before it gives way; the
 * implosion chance is how readily it cracks past that. Stainless steel holds 600 blocks, beyond obsidian (500); titanium, the metal of
 * real deep-diving hulls, 1200, the bottom of the Abyss. Referenced only by the Deep Seas mixin, so nothing here needs Deep Seas loaded.
 */
public final class SubmarineHulls {
    public record Hull(int maxWaterDepth, float implosionChance) { }
    public static final Map<String, Hull> HULLS = Map.of(
        CreatePlant.MOD_ID + ":stainless_steel_block", new Hull(600, .05f),
        CreatePlant.MOD_ID + ":titanium_block", new Hull(1200, .03f));
    private SubmarineHulls() { }
}
