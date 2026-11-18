package net.createplant.mixin;

import java.util.List;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import net.createplant.block.entity.BoilerSteamOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidTankBlockEntity.class)
public class FluidTankBlockEntityMixin {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void createPlant$addSteamPipeOutput(List<Component> tooltip, boolean isPlayerSneaking,
        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        FluidTankBlockEntity tank = (FluidTankBlockEntity) (Object) this;
        FluidTankBlockEntity controller = tank.getControllerBE();
        if (controller == null || !controller.boiler.isActive()) {
            return;
        }

        int outlets = BoilerSteamOutput.countSteamPipeOutlets(controller);
        if (outlets <= 0) {
            return;
        }

        int steam = BoilerSteamOutput.steamGeneratedBy(controller);
        tooltip.add(line("goggle.create_refinery_n_reactors.steam_pushed",
            Component.literal(steam + " mB/t").withStyle(steam > 0 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY)));
        tooltip.add(line("goggle.create_refinery_n_reactors.steam_pipe_outlets",
            Component.literal(Integer.toString(outlets)).withStyle(ChatFormatting.AQUA)));
        net.createplant.compat.display.MachineReadings.compact(tooltip);
        cir.setReturnValue(true);
    }

    private static MutableComponent line(String key, Component value) {
        return Component.literal("  ")
            .append(Component.translatable(key))
            .append(Component.literal(": "))
            .append(value);
    }
}
