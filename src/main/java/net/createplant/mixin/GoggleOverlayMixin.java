package net.createplant.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import java.util.List;
import net.createplant.block.entity.AbsorptionTowerBlockEntity;
import net.createplant.block.entity.LiddedVesselBlockEntity;
import net.createplant.client.ProcessGoggles;
import net.createplant.registry.CEReactor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Create draws the goggle icon over the left of the tooltip; lines that do not start with its indent are hidden behind it. */
@Mixin(value = GoggleOverlayRenderer.class, remap = false)
public class GoggleOverlayMixin {
    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", remap = true))
    private static BlockEntity createPlant$largePressFrame(ClientLevel level, BlockPos pos, Operation<BlockEntity> original) {
        return original.call(level, ProcessGoggles.target(level, pos));
    }

    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/api/equipment/goggles/IHaveGoggleInformation;addToGoggleTooltip(Ljava/util/List;Z)Z"))
    private static boolean createPlant$indent(IHaveGoggleInformation goggles, List<Component> tooltip, boolean sneaking, Operation<Boolean> original) {
        boolean result = original.call(goggles, tooltip, sneaking);
        if (goggles instanceof BlockEntity source) ProcessGoggles.add(source, tooltip, sneaking);
        tooltip.replaceAll(line -> line.getString().isEmpty() || line.getString().startsWith(" ") ? line : Component.literal("    ").append(line));
        return result || !tooltip.isEmpty();
    }

    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiWidth()I"))
    private static int createPlant$fitWidth(GuiGraphics graphics, Operation<Integer> original, @Local(name = "be") BlockEntity source,
        @Local(name = "tooltipTextWidth") int textWidth, @Local(name = "tooltipHeight") int textHeight, @Share("tooltipScale") LocalFloatRef scale) {
        int width = original.call(graphics);
        boolean stirred = source instanceof LiddedVesselBlockEntity vessel && vessel.stirred();
        if (!stirred && !(source instanceof AbsorptionTowerBlockEntity) && (source == null || !source.getBlockState().is(CEReactor.LARGE_ABSORPTION_RING.get()))) return width;
        float fit = Math.min(1, (float) width / (textWidth + 40));
        if (stirred) fit = Math.min(fit, (float) graphics.guiHeight() / (textHeight + 40));
        if (fit >= 1) return width;
        // Create has already pushed this pose. Size and place the icon and tooltip together.
        graphics.pose().scale(fit, fit, 1);
        scale.set(fit);
        return Mth.floor(width / fit);
    }

    @WrapOperation(method = "renderOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;guiHeight()I"))
    private static int createPlant$fitHeight(GuiGraphics graphics, Operation<Integer> original, @Share("tooltipScale") LocalFloatRef scale) {
        int height = original.call(graphics);
        float fit = scale.get();
        return fit > 0 ? Mth.floor(height / fit) : height;
    }
}
