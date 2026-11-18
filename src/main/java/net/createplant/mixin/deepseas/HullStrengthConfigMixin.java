package net.createplant.mixin.deepseas;

import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import java.util.Map;
import net.createplant.compat.deepseas.SubmarineHulls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create: Deep Seas gives a block not in its hull file a depth worked out from its hardness, capped at 400 for other mods' blocks. Its own
 * table of defaults is not capped, so the metal hull blocks go in there; a value already in config/submarine_hull.json still wins.
 */
@Mixin(value = HullStrengthConfig.class, remap = false)
public class HullStrengthConfigMixin {
    @Inject(method = "buildStaticDefaults", at = @At("TAIL"))
    private static void createPlant$metalHulls(Map<String, HullStrengthConfig.HullProperty> defaults, CallbackInfo ci) {
        SubmarineHulls.HULLS.forEach((block, hull) -> defaults.putIfAbsent(block, new HullStrengthConfig.HullProperty(hull.maxWaterDepth(), hull.implosionChance())));
    }
}
