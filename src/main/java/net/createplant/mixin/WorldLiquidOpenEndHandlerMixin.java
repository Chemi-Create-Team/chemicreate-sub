package net.createplant.mixin;

import com.simibubi.create.content.fluids.OpenEndedPipe;
import net.createplant.cloud.GasClouds;
import net.createplant.registry.CETransport;
import net.createplant.registry.CEWorldFluids;
import net.createplant.thermal.FluidThermal;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.simibubi.create.content.fluids.OpenEndedPipe$OpenEndFluidHandler", remap = false)
public class WorldLiquidOpenEndHandlerMixin {
    @Shadow @Final private OpenEndedPipe this$0;

    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void createPlant$fillProcessLiquid(FluidStack resource, FluidAction action, CallbackInfoReturnable<Integer> cir) {
        if (net.createplant.mixture.MixtureHelper.isMixture(resource) && !CETransport.gas(resource)) { cir.setReturnValue(0);return; }
        FluidTank tank = (FluidTank) (Object) this;
        FluidStack held = tank.getFluid();
        if (CETransport.gas(resource)) {
            var world = this$0.getWorld();
            var pos = this$0.getOutputPos();
            if (world == null || world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos) || !world.getBlockState(pos).isAir()
                || !held.isEmpty() && !FluidStack.isSameFluidSameComponents(held, resource)) {
                cir.setReturnValue(0);
                return;
            }
            int accepted = Math.min(resource.getAmount(), tank.getCapacity());
            if (action.execute()) {
                tank.setFluid(FluidStack.EMPTY);
                ((WorldLiquidOpenEndAccess) this$0).createPlant$setPulling(false);
                if (world instanceof ServerLevel server) {
                    var emitted = resource.copyWithAmount(accepted + held.getAmount());
                    net.createplant.safety.RadiationHooks.get().spill(server, pos, emitted);
                    GasClouds.release(server, pos, emitted);
                }
            }
            cir.setReturnValue(accepted);
            return;
        }
        if(!resource.isEmpty() && !net.createplant.item.StatefulBuckets.defaultState(resource)) {
            cir.setReturnValue(0);
            return;
        }
        if (!CEWorldFluids.isLiquid(resource.getFluid()) && !CEWorldFluids.isLiquid(held.getFluid())) return;
        WorldLiquidOpenEndAccess pipe = (WorldLiquidOpenEndAccess) this$0;
        if (resource.isEmpty() || !FluidThermal.valid(resource) || !held.isEmpty() && !FluidThermal.compatible(held, resource)
            || !pipe.createPlant$provideFluid(resource, true)) {
            cir.setReturnValue(0);
            return;
        }
        int accepted = Math.min(resource.getAmount(), tank.getCapacity() - held.getAmount());
        if (action.execute() && accepted > 0) {
            FluidStack filled = FluidThermal.add(held, resource, accepted);
            tank.setFluid(filled);
            pipe.createPlant$setPulling(false);
            if (filled.getAmount() == 1000 && pipe.createPlant$provideFluid(filled, false)) tank.setFluid(FluidStack.EMPTY);
        }
        cir.setReturnValue(accepted);
    }

    @Inject(method = "drainInner", at = @At("HEAD"), cancellable = true)
    private void createPlant$drainProcessLiquid(int amount, FluidStack filter, FluidAction action, CallbackInfoReturnable<FluidStack> cir) {
        FluidTank tank = (FluidTank) (Object) this;
        FluidStack held = tank.getFluid();
        var world = this$0.getWorld();
        var pos = this$0.getOutputPos();
        if (!CEWorldFluids.isLiquid(held.getFluid()) && (world == null || !world.hasChunkAt(pos)
            || !CEWorldFluids.isLiquid(world.getFluidState(pos).getType()))) return;
        if (amount <= 0 || !held.isEmpty() && filter != null && !FluidThermal.compatible(held, filter)) {
            cir.setReturnValue(FluidStack.EMPTY);
            return;
        }
        WorldLiquidOpenEndAccess pipe = (WorldLiquidOpenEndAccess) this$0;
        if (!held.isEmpty()) {
            int drained = Math.min(amount, held.getAmount());
            if (action.execute()) {
                tank.setFluid(drained == held.getAmount() ? FluidStack.EMPTY : held.copyWithAmount(held.getAmount() - drained));
                pipe.createPlant$setPulling(true);
            }
            cir.setReturnValue(held.copyWithAmount(drained));
            return;
        }
        FluidStack source = pipe.createPlant$removeFluid(true);
        if (source.isEmpty() || filter != null && !FluidThermal.compatible(source, filter)) {
            cir.setReturnValue(FluidStack.EMPTY);
            return;
        }
        if (action.execute()) source = pipe.createPlant$removeFluid(false);
        int drained = Math.min(amount, source.getAmount());
        if (action.execute()) {
            tank.setFluid(drained == source.getAmount() ? FluidStack.EMPTY : source.copyWithAmount(source.getAmount() - drained));
            pipe.createPlant$setPulling(true);
        }
        cir.setReturnValue(drained <= 0 ? FluidStack.EMPTY : source.copyWithAmount(drained));
    }
}
