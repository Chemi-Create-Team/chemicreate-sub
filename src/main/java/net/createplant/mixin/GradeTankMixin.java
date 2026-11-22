package net.createplant.mixin;

import java.util.List;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.createplant.thermal.ThermalFluidTank;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=FluidTankBlockEntity.class,remap=false)
public abstract class GradeTankMixin implements net.createplant.block.entity.TankBoiler.Holder {
    @Unique private final net.createplant.thermal.TankHeating createPlant$heating=new net.createplant.thermal.TankHeating();
    @Unique private net.createplant.block.entity.TankBoiler createPlant$boiler;
    @Override public net.createplant.block.entity.TankBoiler createPlant$boiler() {
        if(createPlant$boiler==null)createPlant$boiler=new net.createplant.block.entity.TankBoiler((FluidTankBlockEntity)(Object)this);
        return createPlant$boiler;
    }
    @Inject(method="tick",at=@At("RETURN"))
    private void createPlant$heatContents(CallbackInfo ci) {
        var owner=(FluidTankBlockEntity)(Object)this;var level=owner.getLevel();
        if(net.createplant.block.entity.TankBoiler.applies(owner) && (owner.isController() || createPlant$boiler!=null))createPlant$boiler().tick();
        if(level==null || level.isClientSide() || !owner.isController() || owner.boiler.isActive()
            || (net.createplant.block.entity.TankBoiler.applies(owner) || owner instanceof net.createplant.block.entity.HorizontalTankBlockEntity)
                && net.createplant.thermal.FluidState.liquidWater(owner.getTankInventory().getFluid()) || Math.floorMod(level.getGameTime()+owner.getBlockPos().asLong(),net.createplant.thermal.TankHeating.INTERVAL)!=0)return;
        var base=new java.util.ArrayList<net.minecraft.core.BlockPos>();
        if(!owner.getTankInventory().isEmpty())net.createplant.block.TankCells.forEachBase(owner,base::add);
        int previous=createPlant$heating.progress();
        var inventory=owner.getTankInventory();
        java.util.function.ToIntFunction<FluidStack> capacity=inventory instanceof ThermalFluidTank thermal ? thermal::capacityFor : ignored->inventory.getCapacity();
        if(createPlant$heating.tick(List.of(inventory.getFluid()),net.createplant.thermal.TankHeating.burnerBelow(level,base),capacity))
            owner.getTankInventory().setFluid(owner.getTankInventory().getFluid().copy());
        else if(previous!=createPlant$heating.progress())owner.setChanged();
    }
    @Inject(method="write",at=@At("RETURN"))
    private void createPlant$saveHeating(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider lookup,boolean packet,CallbackInfo ci) {
        if(!packet)createPlant$heating.write(tag,lookup);
        if(createPlant$boiler!=null)createPlant$boiler.write(tag,lookup);
    }
    @Inject(method="read",at=@At("RETURN"))
    private void createPlant$loadHeating(net.minecraft.nbt.CompoundTag tag,net.minecraft.core.HolderLookup.Provider lookup,boolean packet,CallbackInfo ci) {
        if(!packet)createPlant$heating.read(tag,lookup);
        if(createPlant$boiler!=null || tag.contains("BoilerSteam"))createPlant$boiler().read(tag,lookup);
    }
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="applyFluidTankSize",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;drain(ILnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)Lnet/neoforged/neoforge/fluids/FluidStack;"))
    private FluidStack createPlant$keepMixturePrefix(net.neoforged.neoforge.fluids.capability.templates.FluidTank tank,int overflow,net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action,com.llamalad7.mixinextras.injector.wrapoperation.Operation<FluidStack> original) {
        if(!net.createplant.mixture.MixtureHelper.isMixture(tank.getFluid()))return original.call(tank,overflow,action);
        var kept=tank.getFluid().copyWithAmount(tank.getCapacity());var removed=net.createplant.mixture.MixtureHelper.remainder(tank.getFluid(),tank.getCapacity());
        if(action.execute())tank.setFluid(kept);return removed;
    }
    @Unique private static Component createPlant$tankName(FluidTankBlockEntity owner) {
        return owner.getBlockState().getBlock().getName();
    }
    @Inject(method="lambda$registerCapabilities$0",at=@At("RETURN"),cancellable=true)
    private static void createPlant$phasePorts(FluidTankBlockEntity be,net.minecraft.core.Direction side,CallbackInfoReturnable<net.neoforged.neoforge.fluids.capability.IFluidHandler> cir) {
        cir.setReturnValue(net.createplant.block.entity.TankBoiler.port(be,side,net.createplant.thermal.GradeFluidTank.port(be,side,cir.getReturnValue())));
    }
    @Inject(method="getFillState",at=@At("RETURN"),cancellable=true)
    private void createPlant$liquidLevel(CallbackInfoReturnable<Float> cir) {
        var owner=(FluidTankBlockEntity)(Object)this;
        if(owner.getTankInventory() instanceof net.createplant.thermal.GradeFluidTank gas && gas.gasStored() && gas.volumeCapacity()>0) cir.setReturnValue((float)gas.gasVolume()/gas.volumeCapacity());

    }
    private static int gasVolume(FluidTankBlockEntity owner) { return owner.getTankInventory() instanceof net.createplant.thermal.GradeFluidTank gas && gas.gasStored() ? gas.gasVolume() : owner.getTankInventory().getFluidAmount(); }
    private static int volumeCapacity(FluidTankBlockEntity owner) { return owner.getTankInventory() instanceof net.createplant.thermal.GradeFluidTank gas && gas.gasStored() ? gas.volumeCapacity() : owner.getTankInventory().getCapacity(); }
    @Shadow protected abstract void onFluidStackChanged(FluidStack stack);
    @Inject(method="createInventory",at=@At("RETURN"),cancellable=true)
    private void createPlant$thermalInventory(CallbackInfoReturnable<SmartFluidTank> cir) {
        if (((Object)this).getClass()==FluidTankBlockEntity.class && cir.getReturnValue().getClass()==SmartFluidTank.class)
            cir.setReturnValue(new net.createplant.thermal.GradeFluidTank(cir.getReturnValue().getCapacity(),this::onFluidStackChanged,(FluidTankBlockEntity)(Object)this));
    }
    @Inject(method="addToGoggleTooltip",at=@At("RETURN"))
    private void createPlant$temperature(List<Component> lines,boolean sneaking,CallbackInfoReturnable<Boolean> cir) {
        var owner=((FluidTankBlockEntity)(Object)this).getControllerBE();if(owner==null)return;
        // With steam engines attached, Create's own boiler readout is the tank's tooltip.
        if(owner.boiler.isActive()){net.createplant.transport.ContainmentWear.addWarning(owner,lines);return;}
        if (net.createplant.mixture.MixtureHelper.isMixture(owner.getTankInventory().getFluid())) {
            lines.clear();net.createplant.mixture.MixtureGoggleHelper.appendSummary(lines, owner.getTankInventory().getFluid());return;
        }
        var grade=net.createplant.thermal.TankGrade.of(owner);lines.clear();lines.add(createPlant$tankName(owner));
        var fluid = owner.getTankInventory().getFluid();
        lines.add(fluid.isEmpty() ? Component.literal("0 / " + volumeCapacity(owner) + " mB")
            : net.createplant.chemistry.ChemicalReadout.contents(fluid.copyWithAmount(gasVolume(owner)), volumeCapacity(owner)));
        if(grade!=null)grade.describe(lines,fluid,sneaking);
        net.createplant.transport.ContainmentWear.addWarning(owner,lines);
        if(net.createplant.block.entity.TankBoiler.applies(owner))net.createplant.block.entity.TankBoiler.of(owner).goggle(lines);
        net.createplant.compat.display.MachineReadings.compact(lines);
    }
}
