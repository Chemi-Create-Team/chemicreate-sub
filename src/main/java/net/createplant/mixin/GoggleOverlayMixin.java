package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Create draws the goggle icon over the left of the tooltip; lines that do not start with its indent are hidden behind it. */
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public class GoggleOverlayMixin {
    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/equipment/goggles/IHaveGoggleInformation;addToGoggleTooltip(Ljava/util/List;Z)Z"))
    private static boolean createPlant$indent(IHaveGoggleInformation goggles, List<Component> tooltip, boolean sneaking, Operation<Boolean> original) {
        boolean result = original.call(goggles, tooltip, sneaking);
        tooltip.replaceAll(line -> line.getString().isEmpty() || line.getString().startsWith(" ") ? line : Component.literal("    ").append(line));
        return result;
    }
}
