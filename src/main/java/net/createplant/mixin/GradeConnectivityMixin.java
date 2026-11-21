package net.createplant.mixin;

import java.util.ArrayList;
import java.util.List;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createplant.thermal.FluidThermal;
import net.createplant.thermal.GradeFluidTank;
import net.createplant.thermal.ThermalFluidTank;
import net.createplant.mixture.MixtureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.IFluidTank;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ConnectivityHandler.class,remap=false)
public class GradeConnectivityMixin {
    /** Rehoming a running process must not strand its separate feed/product inventories in an inactive member. */
    @Inject(method="tryToFormNewMultiOfWidth",at=@At("HEAD"),cancellable=true)
    private static void createPlant$keepProcessController(BlockEntity be,int width,@Coerce Object cache,boolean simulate,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if(be.getLevel()!=null && be instanceof net.createplant.block.entity.AmmoniaSeparatorBlockEntity drum){
            for(int y=0;y<drum.getMaxLength(net.minecraft.core.Direction.Axis.Y,width);y++)for(int x=0;x<width;x++)for(int z=0;z<width;z++){
                var at=be.getBlockPos().offset(x,y,z);if(!be.getLevel().hasChunkAt(at))continue;
                if(be.getLevel().getBlockEntity(at) instanceof net.createplant.block.entity.AmmoniaSeparatorBlockEntity part && part.getControllerBE() instanceof net.createplant.block.entity.AmmoniaSeparatorBlockEntity owner && owner!=drum && owner.hasProcessContents()){cir.setReturnValue(0);return;}
            }
            return;
        }
        if (!(be instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel) || be.getLevel()==null) return;
        for (int y=0;y<net.createplant.block.ReactorStructure.MAX_SECTIONS;y++) for (int x=0;x<width;x++) for (int z=0;z<width;z++)
            if (be.getLevel().hasChunkAt(be.getBlockPos().offset(x,y,z)) && be.getLevel().getBlockEntity(be.getBlockPos().offset(x,y,z)) instanceof net.createplant.block.entity.LiddedVesselBlockEntity part) {
                var owner=part.getControllerBE();
                if (owner!=null && owner!=vessel && owner.blocksControllerMove()) { cir.setReturnValue(0);return; }
            }
    }
    @WrapOperation(method="tryToFormNewMultiOfWidth",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"))
    private static boolean createPlant$joinMixtures(FluidStack a,FluidStack b,Operation<Boolean> original,BlockEntity be,int width,@Coerce Object cache,boolean simulate){
        if (MixtureHelper.isMixableGas(a) && MixtureHelper.isMixableGas(b)
            && (MixtureHelper.isMixture(a) || MixtureHelper.isMixture(b))) {
            try { MixtureHelper.merge(a, b, false); return true; }
            catch (IllegalArgumentException error) { return false; }
        }
        if(net.createplant.mixture.MixtureHelper.isMixableLiquid(a) && net.createplant.mixture.MixtureHelper.isMixableLiquid(b)
            && (net.createplant.mixture.MixtureHelper.isMixture(a) || net.createplant.mixture.MixtureHelper.isMixture(b)
                || be instanceof net.createplant.block.entity.LiddedVesselBlockEntity vessel && !vessel.hasInternals()))return true;
        return original.call(a,b);
    }
    /** Create merges a part's contents by filling the controller at its old size; a compressed gas is taken whole, since the part is emptied next. */
    @WrapOperation(method="tryToFormNewMultiOfWidth",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/fluids/IFluidTank;fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;)I"))
    private static int createPlant$mergeGas(IFluidTank tank,FluidStack fluid,IFluidHandler.FluidAction action,Operation<Integer> original,@Local(name="tankAt") IFluidTank part) {
        return action.execute() && tank instanceof GradeFluidTank phase && part instanceof GradeFluidTank from && from.gasStored() && phase.absorbGas(fluid) ? fluid.getAmount() : original.call(tank,fluid,action);
    }
    /** Create splits a tank by its per-block liquid capacity, which compressed gas exceeds; each remaining block keeps its share of the volume at the stored pressure instead. */
    @Inject(method="splitMultiAndInvalidate",at=@At("HEAD"))
    private static void createPlant$liftGas(BlockEntity be,@Coerce Object cache,boolean reconnect,CallbackInfo ci,@Share("gas") LocalRef<FluidStack> gas,@Share("blocks") LocalRef<List<BlockPos>> blocks) {
        if(!(be instanceof com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid part) || be.getLevel()==null || be.getLevel().isClientSide()) return;
        BlockEntity owner=part.getControllerBE();
        if(!(owner instanceof com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid root) || root.getWidth()==1 && root.getHeight()==1 || !(root.getTank(0) instanceof GradeFluidTank tank) || !tank.gasStored()) return;
        var positions=new ArrayList<BlockPos>();var origin=owner.getBlockPos();
        for(int y=0;y<root.getHeight();y++) for(int a=0;a<root.getWidth();a++) for(int b=0;b<root.getWidth();b++) positions.add(switch(root.getMainConnectionAxis()) {
            case X -> origin.offset(y,a,b);
            case Y -> origin.offset(a,y,b);
            case Z -> origin.offset(a,b,y);
        });
        gas.set(tank.getFluid().copy());blocks.set(positions);
        tank.setFluid(FluidStack.EMPTY);
    }
    @Inject(method="splitMultiAndInvalidate",at=@At("RETURN"))
    private static void createPlant$shareGas(BlockEntity be,@Coerce Object cache,boolean reconnect,CallbackInfo ci,@Share("gas") LocalRef<FluidStack> gas,@Share("blocks") LocalRef<List<BlockPos>> blocks) {
        FluidStack held=gas.get();
        if(held==null) return;
        List<BlockPos> positions=blocks.get();long total=held.getAmount();int n=positions.size();
        // A removed block's share leaves with it, as Create does for liquids.
        for(int i=0;i<n;i++) {
            int share=(int)(total*(i+1)/n-total*i/n);
            if (share <= 0) continue;
            var parcel = MixtureHelper.portion(held, share);
            held = MixtureHelper.remainder(held, share);
            if(be.getLevel().getBlockEntity(positions.get(i)) instanceof com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid part
                && part.getControllerBE() instanceof com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Fluid root
                && root.getTank(0) instanceof GradeFluidTank tank) tank.absorbGas(parcel);
        }
    }
}
